package mcpatch.gui

import mcpatch.event.Event
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JProgressBar

class McPatchWindow(width: Int = 350, height: Int = 140)
{
    var titleTextSuffix = ""

    private var window = JFrame()
    private var label = JLabel("空标签空标签空标签空标签空标签空标签空标签空标签空标签").apply { setBounds(30, 15, 275, 20); horizontalAlignment = JLabel.CENTER; window.contentPane.add(this) }
    private var progressBar = JProgressBar(0, 1000).apply { setBounds(35, 50, 265, 30); isStringPainted = true; window.contentPane.add(this) }

    val onWindowClosing = Event<McPatchWindow>()

    init {
        window.isUndecorated = false
        window.contentPane.layout = null
        window.isVisible = false
        window.setSize(width, height)
        window.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
        window.setLocationRelativeTo(null)
        window.isResizable = false
//        window.isAlwaysOnTop = true

        window.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                onWindowClosing.invoke(this@McPatchWindow)
            }
        })
    }

    /**
     * 标题栏文字
     */
    var titleText: String
        get() = window.title
        set(value) = run { window.title = value + titleTextSuffix }

    /**
     * 显示窗口
     */
    fun show() = window.run { isVisible = true }

    /**
     * 隐藏窗口
     */
    fun hide() = window.run { isVisible = false }

    /**
     * 销毁窗口
     */
    fun destroy() = window.dispose()

    /**
     * 进度条上的文字
     */
    var progressBarText: String
        get() = progressBar.string
        set(value) = run { progressBar.string = value }

    /**
     * 进度条的值
     */
    var progressBarValue: Int
        get() = progressBar.value
        set(value) = run { progressBar.value = value }

    /**
     * 标签上的文字
     */
    var labelText: String
        get() = label.text
        set(value) = run { label.text = value }


    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            McPatchWindow(300, 120).show()
        }
    }
}