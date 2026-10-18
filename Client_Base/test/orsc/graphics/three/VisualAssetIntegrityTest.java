package orsc.graphics.three;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.model.Sprite;
import com.openrsc.data.DataConversions;
import com.openrsc.data.DataFileDecrypter;
import com.openrsc.data.DataOperations;
import orsc.graphics.two.SpriteArchive.Entry;
import orsc.graphics.two.SpriteArchive.Subspace;
import orsc.graphics.two.SpriteArchive.Unpacker;
import orsc.graphics.two.SpriteArchive.Workspace;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Headless integrity check for the shipped visual assets (see
 * scripts/check_visual_assets.sh):
 *
 * <ul>
 *   <li>Every model registered by {@link EntityHandler} (objects, NPCs, spell
 *       effects) must resolve to a parseable entry in models.orsc with at least
 *       one face. A missing name used to degrade to a silent 1x1 placeholder
 *       model (e.g. the runiteruck1 typo), which renders as an invisible
 *       object in the world.</li>
 *   <li>Every material reference on every model face (faceTextureFront/Back)
 *       must be the TRANSPARENT sentinel, a negative "untextured" value, or a
 *       texture index below the largest shipped texture set. Indices at or
 *       above the active set are clamped to texture 0 by the renderer; refs
 *       beyond every shipped set would be pure data loss.</li>
 *   <li>The authentic texture set must contain all {@code textureCount()}
 *       sprites at ids 3225.., each square 64/128 with boundWidth == width so
 *       the m_Hb shade flag (boundWidth/64-1) is correct. The custom set's
 *       texture names must cover "0"..count-1 exactly once - the loader
 *       resolves them by NAME through a HashMap, not by list position - and
 *       each entry must likewise be square and bound-consistent.</li>
 *   <li>The authentic texture id block (3225..) must end before the crown /
 *       window icon block at 3284, that icon block must stay
 *       non-texture-shaped, no model ref beyond the authentic set may have
 *       texture-shaped art in Authentic_Sprites.orsc (there is none for the
 *       tree/bush textures - the authentic path shades them as texture 0 on
 *       purpose), and {@code mudclient.loadTexturesAuthentic} must stay bound
 *       to {@code EntityHandler.textureCount()} with its single
 *       {@code loadSprite(spriteTexture + i, ...)} call. This pins the
 *       2026-10-11 probe finding that ids 3280..3291 are gap sprites plus UI
 *       icons, NOT the 12 missing tree textures an earlier note claimed:
 *       feeding them to Scene.setFrustum (which reads a full 64^2/128^2 index
 *       block) throws AIOOBE during texture load.</li>
 * </ul>
 *
 * Plain {@code main} on purpose, like ThemeParityTest: no test framework, must
 * run on the JDK 8 bundled in Portable_Windows/. Run with cwd = Client_Base so
 * the Cache/video relative paths resolve (scripts/check_visual_assets.sh does
 * this).
 */
public final class VisualAssetIntegrityTest {

	private static int checks = 0;
	private static int failures = 0;

	/** Every distinct material ref seen on shipped models: ref -> first model name. */
	private static final Map<Integer, String> modelTextureRefs = new TreeMap<Integer, String>();

	public static void main(String[] args) throws Exception {
		// Same load the client performs (MEMBER_WORLD=false); only items are
		// gated by loadMembers, so all textures / object / NPC defs register.
		EntityHandler.load(false);
		int textureCount = EntityHandler.textureCount();
		check(textureCount > 0, "texture definitions loaded (" + textureCount + ")");

		// The 20 spell-effect model names mudclient.loadModels() appends on top
		// of the def-registered names before parsing models.orsc.
		String[] spellEffectModels = {
			"torcha2", "torcha3", "torcha4", "skulltorcha2", "skulltorcha3", "skulltorcha4",
			"firea2", "firea3", "fireplacea2", "fireplacea3", "firespell2", "firespell3",
			"lightning2", "lightning3", "clawspell2", "clawspell3", "clawspell4", "clawspell5",
			"spellcharge2", "spellcharge3"
		};
		for (String name : spellEffectModels) {
			EntityHandler.storeModel(name);
		}

		int customTextureCount = verifyCustomTextures();
		// The renderer sizes its texture arrays to whichever set is active and
		// clamps out-of-range face refs to texture 0, so the hard invariant is:
		// every ref must resolve in the LARGEST shipped set.
		int maxTextureCount = Math.max(textureCount, customTextureCount);

		verifyAuthenticTextures(textureCount);
		verifyModels(maxTextureCount);
		verifyShadeClamp(textureCount);
		verifyTextureBlockLayout(textureCount, maxTextureCount);
		verifyTextureLoaderWiring();

		System.out.println((failures == 0 ? "OK" : "FAIL") + ": " + checks
			+ " visual-asset integrity checks, " + failures + " failed."
			+ " (textures=" + textureCount + " customTextures=" + customTextureCount + ")");
		if (failures != 0) {
			System.exit(1);
		}
	}

	/** Authentic zip path (default S_WANT_CUSTOM_SPRITES=false): ids 3225.. */
	private static void verifyAuthenticTextures(int textureCount) throws Exception {
		File file = new File("Cache/video/Authentic_Sprites.orsc");
		check(file.exists(), "authentic sprite archive present");
		if (!file.exists()) {
			return;
		}
		ZipFile zip = new ZipFile(file);
		int base = 3225; // mudclient.spriteTexture
		for (int i = 0; i < textureCount; i++) {
			int spriteId = base + i;
			ZipEntry entry = zip.getEntry(String.valueOf(spriteId));
			if (entry == null) {
				fail("authentic texture " + i + ": sprite " + spriteId + " missing from archive");
				continue;
			}
			ByteBuffer buf = DataConversions.streamToBuffer(new BufferedInputStream(zip.getInputStream(entry)));
			Sprite sprite;
			try {
				sprite = Sprite.unpack(buf);
			} catch (Exception ex) {
				fail("authentic texture " + i + ": sprite " + spriteId + " fails to unpack: " + ex);
				continue;
			}
			int w = sprite.getWidth();
			check(w == sprite.getHeight() && (w == 64 || w == 128),
				"authentic texture " + i + " square 64/128 (" + w + "x" + sprite.getHeight() + ")");
			// boundWidth drives the m_Hb shade flag (boundWidth/64-1): it must
			// equal the pixel width or the texture shades as the wrong size.
			check(sprite.getSomething1() == w,
				"authentic texture " + i + " boundWidth==width (" + sprite.getSomething1() + " vs " + w + ")");
		}
		zip.close();
	}

	/**
	 * Custom osar path (S_WANT_CUSTOM_SPRITES=true): named "textures" subspace.
	 * Returns its entry count.
	 *
	 * <p>The entries are stored in lexicographic name order ("0", "1", "10",
	 * ...), but the runtime loader never reads them positionally:
	 * GraphicsController.fillSpriteTree() builds a HashMap keyed by entry id
	 * and mudclient.loadTextures() resolves spriteTree.get("textures").get(
	 * String.valueOf(i)) for i in 0..size-1. So the invariant that keeps the
	 * loader from NPE-ing is name-set completeness + uniqueness, not order.
	 */
	private static int verifyCustomTextures() {
		File file = new File("Cache/video/Custom_Sprites.osar");
		check(file.exists(), "custom sprite archive present");
		if (!file.exists()) {
			return 0;
		}
		Workspace workspace = new Unpacker().unpackArchive(file);
		if (workspace == null) {
			fail("custom sprite archive fails to unpack");
			return 0;
		}
		Subspace textures = workspace.getSubspaceByName("textures");
		if (textures == null) {
			fail("custom sprite archive has no 'textures' subspace");
			return 0;
		}
		Set<String> names = new HashSet<String>();
		for (int i = 0; i < textures.getEntryCount(); i++) {
			Entry entry = textures.getEntryList().get(i);
			names.add(entry.getID());
			Sprite sprite = entry.getFrames()[0].getSprite();
			int w = sprite.getWidth();
			check(w == sprite.getHeight() && (w == 64 || w == 128),
				"custom texture " + entry.getID() + " square 64/128 (" + w + "x" + sprite.getHeight() + ")");
			check(sprite.getSomething1() == w,
				"custom texture " + entry.getID() + " boundWidth==width (" + sprite.getSomething1() + " vs " + w + ")");
		}
		check(names.size() == textures.getEntryCount(),
			"custom texture ids unique (" + names.size() + " of " + textures.getEntryCount() + ")");
		for (int i = 0; i < textures.getEntryCount(); i++) {
			check(names.contains(String.valueOf(i)), "custom texture name \"" + i + "\" present for loader lookup");
		}
		return textures.getEntryCount();
	}

	/** Every registered model must exist, parse, and only ref textures the renderer can hold. */
	private static void verifyModels(int maxTextureCount) throws Exception {
		File file = new File("Cache/video/models.orsc");
		check(file.exists(), "model archive present");
		if (!file.exists()) {
			return;
		}
		byte[] data = unpackModels(file);
		check(data != null && data.length > 0, "model archive unpacks");
		if (data == null) {
			return;
		}

		int modelCount = EntityHandler.getModelCount();
		for (int i = 0; i < modelCount; i++) {
			String name = EntityHandler.getModelName(i);
			if (name == null) {
				continue;
			}
			int offset = DataOperations.getDataFileOffset(name + ".ob3", data);
			if (offset == 0) {
				fail("model '" + name + "' (index " + i + ") missing from models.orsc"
					+ " -> renders as silent 1x1 placeholder");
				continue;
			}
			RSModel model;
			try {
				model = new RSModel(data, offset, true);
			} catch (Throwable t) {
				fail("model '" + name + "' fails to parse: " + t);
				continue;
			}
			check(model.vertHead > 0 && model.faceHead > 0,
				"model '" + name + "' has geometry (v=" + model.vertHead + " f=" + model.faceHead + ")");
			verifyModelMaterials(name, model, maxTextureCount);
		}
	}

	private static void verifyModelMaterials(String name, RSModel model, int maxTextureCount) {
		for (int face = 0; face < model.faceHead; face++) {
			verifyMaterialRef(name, face, "front", model.faceTextureFront[face], maxTextureCount);
			verifyMaterialRef(name, face, "back", model.faceTextureBack[face], maxTextureCount);
		}
	}

	private static void verifyMaterialRef(String name, int face, String side, int ref, int maxTextureCount) {
		// TRANSPARENT = no texture; negative = untextured sentinel (renderer
		// guards m_b >= 0). Anything else must be a real index the renderer
		// can hold: Scene clamps refs >= the active set to texture 0.
		if (ref == Scene.TRANSPARENT || ref < 0) {
			return;
		}
		if (!modelTextureRefs.containsKey(ref)) {
			modelTextureRefs.put(ref, name);
		}
		if (ref >= maxTextureCount) {
			fail("model '" + name + "' face " + face + " " + side + " texture ref " + ref
				+ " beyond every shipped set (authentic/custom max " + maxTextureCount + ")");
		} else {
			check(true, "model '" + name + "' face " + face + " " + side + " texture ref " + ref + " in range");
		}
	}

	/** Same 6-byte header + optional BSC decompress as mudclient.unpackData, without the client port. */
	private static byte[] unpackModels(File file) throws Exception {
		InputStream in = new FileInputStream(file);
		DataInputStream din = new DataInputStream(in);
		byte[] headers = new byte[6];
		din.readFully(headers, 0, 6);
		int decmpLen = ((headers[0] & 0xFF) << 16) + ((headers[1] & 0xFF) << 8) + (headers[2] & 0xFF);
		int cmpLen = ((headers[3] & 0xFF) << 16) + ((headers[4] & 0xFF) << 8) + (headers[5] & 0xFF);
		byte[] cmp = new byte[cmpLen];
		int read = 0;
		while (read < cmpLen) {
			int want = Math.min(1000, cmpLen - read);
			din.readFully(cmp, read, want);
			read += want;
		}
		din.close();
		if (cmpLen != decmpLen) {
			byte[] out = new byte[decmpLen];
			DataFileDecrypter.unpackData(out, decmpLen, cmp, cmpLen, 0);
			return out;
		}
		return cmp;
	}

	/**
	 * Scene.textureShadeIndex is the clamp that keeps refs beyond the active
	 * texture set from indexing m_Hb out of bounds mid-render (authentic set
	 * holds 55; shipped models ref up to 66). It must mirror the textured
	 * draw path: valid refs pass through, anything else falls back to 0.
	 */
	private static void verifyShadeClamp(int textureCount) {
		check(Scene.textureShadeIndex(0, textureCount) == 0,
			"shade clamp passes texture 0 through");
		check(Scene.textureShadeIndex(textureCount - 1, textureCount) == textureCount - 1,
			"shade clamp passes last valid texture through");
		check(Scene.textureShadeIndex(textureCount, textureCount) == 0,
			"shade clamp falls back to 0 at the set boundary");
		check(Scene.textureShadeIndex(66, 55) == 0,
			"shade clamp falls back to 0 for the highest shipped ref (66) under the authentic set (55)");
		check(Scene.textureShadeIndex(63, 55) == 0 && Scene.textureShadeIndex(64, 55) == 0,
			"shade clamp falls back to 0 for refs 63/64 under the authentic set");
		check(Scene.textureShadeIndex(66, 67) == 66,
			"shade clamp keeps ref 66 under the custom set (67)");
	}

	/**
	 * The authentic texture id block is spriteTexture(3225)..+textureCount-1.
	 * Two invariants pin the 2026-10-11 full-archive probe finding that ids
	 * 3280..3291 are NOT "12 extra tree textures":
	 *
	 * <ul>
	 *   <li>The block must end at or before 3283 - i.e. stay clear of the
	 *       crown/window icon block that loadMediaAuthentic claims with
	 *       {@code loadSprite(3284, "media", 11)} + {@code setIconsStart(0,
	 *       3284)}. Growing textureCount into it overwrites icon sprite slots
	 *       and then crashes Scene.setFrustum with AIOOBE: those sprites are
	 *       9x9/13x11, while setFrustum reads a full 64^2/128^2 index block
	 *       (m_Hb = boundWidth/64-1 makes 13x11 behave as "128").</li>
	 *   <li>For every model ref beyond the authentic set, the corresponding
	 *       sprite (3225+ref) must NOT be texture-shaped: fingerprinting all
	 *       2259 archive entries found no such art anywhere, so texture-0
	 *       fallback is the correct authentic-path behaviour and only the
	 *       custom archive carries these textures. If this ever fails, real
	 *       art was added - review the loader and this guard together.</li>
	 * </ul>
	 */
	private static void verifyTextureBlockLayout(int textureCount, int maxTextureCount) throws Exception {
		int base = 3225; // mudclient.spriteTexture
		int iconBlockStart = 3284; // loadMediaAuthentic: loadSprite(3284, "media", 11) + setIconsStart(0, 3284)
		check(base + textureCount <= iconBlockStart,
			"authentic texture block (" + base + ".." + (base + textureCount - 1)
				+ ") stays clear of the crown/window icon block at " + iconBlockStart);

		File file = new File("Cache/video/Authentic_Sprites.orsc");
		if (!file.exists()) {
			return; // presence already failed in verifyAuthenticTextures
		}
		ZipFile zip = new ZipFile(file);
		try {
			// The 11 media icons at 3284..3294: must exist and must not be
			// mistaken for world textures (they are 9x9/13x11 UI art).
			for (int id = iconBlockStart; id <= iconBlockStart + 10; id++) {
				ZipEntry entry = zip.getEntry(String.valueOf(id));
				if (entry == null) {
					fail("media icon sprite " + id + " missing from authentic archive");
					continue;
				}
				Sprite sprite = Sprite.unpack(DataConversions.streamToBuffer(
					new BufferedInputStream(zip.getInputStream(entry))));
				int w = sprite.getWidth();
				boolean textureShaped = w == sprite.getHeight() && (w == 64 || w == 128)
					&& sprite.getSomething1() == w;
				check(!textureShaped,
					"media icon " + id + " is not texture-shaped (" + w + "x" + sprite.getHeight() + ")");
			}

			// Refs the authentic set does not cover: no texture-shaped art may
			// exist at their would-be sprite id, or the fallback contract is
			// no longer true and the loader must be extended deliberately.
			for (Map.Entry<Integer, String> me : modelTextureRefs.entrySet()) {
				int ref = me.getKey();
				if (ref < textureCount) {
					continue;
				}
				int spriteId = base + ref;
				ZipEntry entry = zip.getEntry(String.valueOf(spriteId));
				if (entry == null) {
					check(true, "model '" + me.getValue() + "' ref " + ref + ": authentic sprite "
						+ spriteId + " absent - confirmed no authentic art (texture-0 fallback)");
					continue;
				}
				Sprite sprite = Sprite.unpack(DataConversions.streamToBuffer(
					new BufferedInputStream(zip.getInputStream(entry))));
				int w = sprite.getWidth();
				boolean textureShaped = w == sprite.getHeight() && (w == 64 || w == 128)
					&& sprite.getSomething1() == w;
				check(!textureShaped,
					"model '" + me.getValue() + "' ref " + ref + ": authentic sprite " + spriteId
						+ " is not texture-shaped (" + w + "x" + sprite.getHeight()
						+ ") - no authentic art exists for this ref; texture-0 fallback is correct");
			}
		} finally {
			zip.close();
		}
	}

	/**
	 * Source-level wiring tripwire: the authentic texture loader must stay
	 * driven by {@code EntityHandler.textureCount()} from
	 * {@code mudclient.spriteTexture}, and no literal sprite id may be loaded
	 * with package "texture". Hardcoding an extra range (e.g.
	 * {@code loadSprite(3280, "texture", 12)}) is exactly the change this
	 * guard exists to block: 3280..3283 are unused gap sprites (one is a
	 * non-square 51x128) and 3284..3294 are window/crown icons - feeding any
	 * of them to Scene.setFrustum throws AIOOBE during texture load.
	 */
	private static void verifyTextureLoaderWiring() throws Exception {
		File src = new File("src/orsc/mudclient.java");
		check(src.exists(), "mudclient source present for loader-wiring check");
		if (!src.exists()) {
			return;
		}
		String text = new String(Files.readAllBytes(src.toPath()), StandardCharsets.UTF_8);
		int sig = text.indexOf("private void loadTexturesAuthentic");
		int next = sig >= 0 ? text.indexOf("private void loadSprite(", sig) : -1;
		check(sig >= 0 && next > sig, "loadTexturesAuthentic method located in mudclient.java");
		if (sig < 0 || next <= sig) {
			return;
		}
		String body = text.substring(sig, next);
		check(body.contains("i < EntityHandler.textureCount()"),
			"loadTexturesAuthentic loop bound is EntityHandler.textureCount()");
		int loads = countOccurrences(body, "loadSprite(");
		check(loads == 1, "loadTexturesAuthentic performs exactly one loadSprite (found " + loads + ")");
		check(body.contains("loadSprite(spriteTexture + i"),
			"loadSprite in loadTexturesAuthentic uses spriteTexture + i base id");
		check(!Pattern.compile("loadSprite\\(\\s*\\d+\\s*,\\s*\\\"texture\\\"").matcher(text).find(),
			"no literal sprite id is loaded with package \"texture\" anywhere in mudclient.java");
	}

	private static int countOccurrences(String haystack, String needle) {
		int count = 0;
		int idx = 0;
		while ((idx = haystack.indexOf(needle, idx)) >= 0) {
			count++;
			idx += needle.length();
		}
		return count;
	}

	private static void check(boolean ok, String label) {
		checks++;
		if (!ok) {
			failures++;
			System.out.println("  FAIL: " + label);
		}
	}

	private static void fail(String message) {
		checks++;
		failures++;
		System.out.println("  FAIL: " + message);
	}
}
