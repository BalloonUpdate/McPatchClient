package GUI;

import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatAtomOneDarkContrastIJTheme;

import javax.swing.*;
import java.awt.*;

/**
 * @author Kasumi_Nova
 * A class for tooling, which is used to decorate Swing interface
 */
public class SetupSwing {
    public static void init() {
        // antialias
        System.setProperty("awt.useSystemAAFontSettings", "lcd");
        System.setProperty("swing.aatext", "true");

        // use standalone thread to take effect
        Thread uiThread = new Thread(() -> {
            // set the corner radius
            UIManager.put("Button.arc", 7);
            UIManager.put("Component.arc", 7);
            UIManager.put("ProgressBar.arc", 7);
            UIManager.put("TextComponent.arc", 5);
            UIManager.put("CheckBox.arc", 3);
            // set the scroll bar
            UIManager.put("ScrollBar.showButtons", false);
            UIManager.put("ScrollBar.thumbArc", 7);
            UIManager.put("ScrollBar.width", 12);
            UIManager.put("ScrollBar.thumbInsets", new Insets(2,2,2,2));
            UIManager.put("ScrollBar.track", new Color(0,0,0,0));
            // set the tab separator
            UIManager.put("TabbedPane.showTabSeparators", true);
        });
        uiThread.start();

        Thread themeThread = new Thread(() -> {
            // set up
            try {
                UIManager.setLookAndFeel(new FlatAtomOneDarkContrastIJTheme());
            } catch (Exception e) {
                System.err.println("Failed to initialize LaF");
                e.printStackTrace();
            }
        });
        themeThread.start();

        try {
            uiThread.join();
            themeThread.join();
        } catch (InterruptedException ignored) {}
    }
}
