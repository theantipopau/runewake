package launcher;

import launcher.Fancy.MainWindow;
import launcher.Gameupdater.ClientUpdater;
import launcher.Utils.Defaults;
import launcher.Utils.Logger;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.net.URLConnection;

public class Launcher extends Component {
  public static ImageIcon icon = null;
  private JProgressBar m_progressBar;
  public static ClientUpdater updater;

  public void initializeLauncher() {
    Settings.loadSettings();

    // Add progress bar
    m_progressBar = new JProgressBar();

    if (Main.disabledUpdate) {
      Settings.autoUpdate = false;
      Settings.saveSettings();
    } else if (Settings.firstRun) {
      int response = JOptionPane.showConfirmDialog(
          this,
          "The OpenRSC Launcher has an automatic update feature.\n"
              + "\n"
              + "Would you like to enable this feature?\n",
          "OpenRSC",
          JOptionPane.YES_NO_OPTION,
          JOptionPane.INFORMATION_MESSAGE,
          icon);
      if (response == JOptionPane.YES_OPTION || response == JOptionPane.CLOSED_OPTION) {
        Settings.autoUpdate = true;
        JOptionPane.showMessageDialog(
            this,
            "The OpenRSC Launcher is set to check for updates at every launch!",
            "OpenRSC",
            JOptionPane.INFORMATION_MESSAGE,
            icon);
      } else if (response == JOptionPane.NO_OPTION) {
        Settings.autoUpdate = false;
        JOptionPane.showMessageDialog(
            this,
            "The OpenRSC launcher will not check for updates automatically.\n"
                + "\n"
                + "You will not get notified when new releases are available. To update the launcher, you\n"
                + "will need to do it manually by replacing 'OpenRSC.jar'.\n"
                + "\n"
                + "You can enable automatic updates again in the localSettings.conf file.",
            "OpenRSC",
            JOptionPane.INFORMATION_MESSAGE,
            icon);
      }
      Settings.saveSettings();
    }

    if (Settings.autoUpdate) {
      setStatus("Checking for launcher update...");
      double latestVersion = fetchLatestVersionNumber();
      if (Defaults._CURRENT_VERSION < latestVersion) {
        setStatus("Launcher update is available");
        int response = JOptionPane.showConfirmDialog(
            this,
            "A launcher update is available!\n"
                + "\n"
                + "Latest: "
                + String.format("%8.6f", latestVersion)
                + "\n"
                + "Installed: "
                + String.format("%8.6f", Defaults._CURRENT_VERSION)
                + "\n"
                + "\n"
                + "Would you like to update now?",
            "OpenRSC",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.INFORMATION_MESSAGE,
            icon);
        if (response == JOptionPane.YES_OPTION) {
          if (updateJar()) {
            JOptionPane.showMessageDialog(
                this,
                "The launcher has been updated successfully!\n"
                    + "\n"
                    + "The launcher requires a restart, and will now exit.",
                "OpenRSC",
                JOptionPane.INFORMATION_MESSAGE,
                icon);
            System.exit(0);
          } else {
            response = JOptionPane.showConfirmDialog(
                this,
                "The launcher has failed to update, please try again later.\n"
                    + "\n"
                    + "Would you like to continue without updating?",
                "OpenRSC",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE,
                icon);
            if (response == JOptionPane.NO_OPTION || response == JOptionPane.CLOSED_OPTION) {
              System.exit(0);
            }
          }
        }
      }
    }

    // Initialize UI
    final MainWindow frame = new MainWindow();
    frame.build();

    // Fetch OpenRSC client jar and cache updates; also init progress bar.
    // Previously ran unconditionally regardless of --no-update/-n, even though that flag's own
    // help text says it "disables autoupdate" - the flag only skipped the launcher's own
    // self-update prompt above, not this actual client/cache download. Gating this too makes
    // --no-update actually mean what it says, and gives a safe way to run the launcher without
    // hitting the configured file server at all.
    if (!Main.disabledUpdate) {
      updater = new ClientUpdater(Main.configFileLocation);
      updater.updateOpenRSCClient();
    }
  }

  public static Double fetchLatestVersionNumber() {
    try {
      double currentVersion = 0.0;
      URL updateURL = new URL(Defaults._VERSION_UPDATE_URL);

      // Open connection
      URLConnection connection = updateURL.openConnection();
      connection.setConnectTimeout(3000);
      connection.setReadTimeout(3000);
      BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
      String line;
      while ((line = in.readLine()) != null) {
        if (line.contains("_CURRENT_VERSION")) {
          currentVersion = Double.parseDouble(line.substring(line.indexOf('=') + 1, line.indexOf(';')));
          Logger.Info("Current Version: " + currentVersion);
          break;
        }
      }

      // Close connection
      in.close();
      return currentVersion;
    } catch (Exception e) {
      Logger.Error("Error checking latest version");
      return Defaults._CURRENT_VERSION;
    }
  }

  public boolean updateJar() {
    setStatus("Starting launcher update...");
    setProgress(0, 1);

    File file = new File("./" + Defaults._LAUNCHER_FILENAME);
    File temporaryFile = new File("./" + Defaults._LAUNCHER_FILENAME + ".part");
    try {
      URL url = new URL(Defaults._GAME_FILES_SERVER + Defaults._LAUNCHER_FILENAME);

      // Use the same connection for metadata and content so its timeouts apply
      // to the complete request. A blocked update host must not freeze launch.
      URLConnection connection = url.openConnection();
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(5000);
      int size = connection.getContentLength();

      int offset = 0;
      byte[] data = new byte[8192];
      try (InputStream input = connection.getInputStream();
          FileOutputStream output = new FileOutputStream(temporaryFile)) {
        int readSize;
        while ((readSize = input.read(data)) != -1) {
          output.write(data, 0, readSize);
          offset += readSize;
          if (size > 0) {
            setStatus("Updating launcher (" + (offset / 1024) + "KiB / " + (size / 1024) + "KiB)");
            setProgress(offset, size);
          } else {
            setStatus("Updating launcher (" + (offset / 1024) + "KiB)");
          }
        }
      }
      Files.move(temporaryFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
      return true;
    } catch (Exception error) {
      // Do not claim success after a partial download, and do not destroy the
      // currently installed launcher if the transfer or staged move fails.
      if (temporaryFile.exists()) {
        temporaryFile.delete();
      }
      Logger.Error("Unable to download launcher update: " + error.getMessage());
      return false;
    }
  }

  /**
   * Sets the progress value of the launcher progress bar.
   *
   * @param value the number of tasks that have been completed
   * @param total the total number of tasks to complete
   */
  public void setProgress(final int value, final int total) {
    SwingUtilities.invokeLater(
        new Runnable() {
          @Override
          public void run() {
            if (total == 0) {
              m_progressBar.setValue(0);
              return;
            }

            m_progressBar.setValue(value * 100 / total);
          }
        });
  }

  /**
   * Changes the launcher progress bar text and pauses the thread for 5 seconds.
   *
   * @param text the text to change the progress bar text to
   */
  public void error(String text) {
    setStatus("Error: " + text);
    try {
      Thread.sleep(5000);
      System.exit(0);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void setStatus(final String text) {
    SwingUtilities.invokeLater(
        new Runnable() {
          @Override
          public void run() {
            m_progressBar.setString(text);
          }
        });
  }
}
