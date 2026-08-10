using System;
using System.Diagnostics;
using System.IO;
using System.Windows.Forms;

namespace RuneWakeLauncher
{
    // Thin native launcher stub: locates the bundled JRE next to this exe and runs the
    // Java launcher jar (which handles update-checking and starting the actual game client).
    // Kept intentionally minimal - all real logic (updating, launching the client) already
    // lives in the existing Java launcher (PC_Launcher / RuneWakeLauncher.jar); this exe just
    // gives players a normal double-clickable Windows application instead of a .bat/.jar.
    internal static class Program
    {
        [STAThread]
        private static void Main(string[] args)
        {
            string installDir = AppDomain.CurrentDomain.BaseDirectory;
            string javawPath = Path.Combine(installDir, "jre", "bin", "javaw.exe");
            string launcherJar = Path.Combine(installDir, "RuneWakeLauncher.jar");

            if (!File.Exists(javawPath))
            {
                MessageBox.Show(
                    "Could not find the bundled Java runtime (jre\\bin\\javaw.exe).\n" +
                    "Please reinstall RuneWake.",
                    "RuneWake", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return;
            }

            if (!File.Exists(launcherJar))
            {
                MessageBox.Show(
                    "Could not find RuneWakeLauncher.jar.\nPlease reinstall RuneWake.",
                    "RuneWake", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return;
            }

            string extraArgs = string.Join(" ", args);
            var startInfo = new ProcessStartInfo
            {
                FileName = javawPath,
                Arguments = "-Xms312m -Dsun.java2d.opengl=true -jar \"" + launcherJar + "\" " + extraArgs,
                WorkingDirectory = installDir,
                UseShellExecute = false,
            };

            try
            {
                Process.Start(startInfo);
            }
            catch (Exception ex)
            {
                MessageBox.Show(
                    "Failed to start RuneWake:\n" + ex.Message,
                    "RuneWake", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }
    }
}
