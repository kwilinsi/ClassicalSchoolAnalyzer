package gui.windows;

import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import main.Main;

public class MyBaseWindow extends BasicWindow {
    public MyBaseWindow(String title) {
        super(title);
    }

    public MyBaseWindow() {
        this("");
    }

    /**
     * Handle keyboard input passed to this window. First, this tries passing the input to the superclass. If the
     * superclass doesn't know what to do with it, it checks if the user pressed Ctrl+X, in which case the window is
     * closed and {@link Main#shutdown()} is called. Otherwise, it returns whatever the superclass method returned
     * (probably <code>false</code>).
     *
     * @param key The keyboard input to handle.
     * @return <code>True</code> if and only if the input was handled.
     */
    @Override
    public boolean handleInput(KeyStroke key) {
        boolean handled = super.handleInput(key);
        if (!handled) {
            // If the user pressed Ctrl+X, close the window and return true
            if (key.getKeyType() == KeyType.Character && key.getCharacter() == 'x' && key.isCtrlDown()) {
                close();
                Main.shutdown();
                return true;
            }
        }
        return handled;
    }
}
