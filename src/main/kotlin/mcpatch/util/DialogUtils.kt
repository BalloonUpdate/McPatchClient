package mcpatch.util

import javax.swing.JOptionPane

object DialogUtils
{
    @JvmStatic
    fun confirm(title: String, content: String): Boolean
            = JOptionPane.showConfirmDialog(null, content, title, JOptionPane.YES_NO_OPTION) == 0

    @JvmStatic
    fun error(title: String, content: String)
            = JOptionPane.showMessageDialog(null, content, title, JOptionPane.ERROR_MESSAGE)

    @JvmStatic
    fun info(title: String, content: String)
            = JOptionPane.showMessageDialog(null, content, title, JOptionPane.INFORMATION_MESSAGE)
}