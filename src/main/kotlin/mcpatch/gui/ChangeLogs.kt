package mcpatch.gui

import com.github.kasuminova.GUI.SetupSwing
import java.awt.BorderLayout
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*
import javax.swing.border.EmptyBorder

val testText = "Java平台由Java虚拟机(Java Virtual Machine)和Java 应用编程接口(Application Programming Interface、简称API)构成。\n" +
        "Java 应用编程接口为Java应用提供了一个独立于操作系统的标准接口，可分为基本部分和扩展部分。\n" +
        "在硬件或操作系统平台上安装一个Java平台之后，Java应用程序就可运行。Java平台已经嵌入了几乎所有的操作系统。\n" +
        "这样Java程序可以只编译一次，就可以在各种系统中运行。Java应用编程接口已经从1.1x版发展到1.2版。常用的Java平台基于Java1.8，最近版本为Java19。"

class ChangeLogs
{
    private var window = JFrame()
    private var panel = JPanel()
    private var panel2 = JPanel();
    private var changlogs = JTextArea()
    private var closeButton = JButton("关闭")

    private var threadLock = Thread {
        try {
            while (true)
                Thread.sleep(1000)
        } catch (_: Exception) {  }
    }

    private var autoCloseDelay = 0

    private var autoCloseThread = Thread {
        Thread.sleep(autoCloseDelay.toLong())
        close()
    }

    init {
        window.isUndecorated = false
        window.contentPane = panel
        window.isVisible = true
        window.setSize(400, 300)
        window.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        window.setLocationRelativeTo(null)
        window.isResizable = true

        window.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent?) {
                threadLock.interrupt()
            }
        })

        // esc 关闭
        window.addKeyListener(object : KeyListener {
            override fun keyTyped(e: KeyEvent) {}

            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ESCAPE)
                    close()
            }

            override fun keyReleased(e: KeyEvent) {}
        })

        panel.border = EmptyBorder(4, 4, 4, 4)
        panel.layout = BorderLayout(8, 4)
        panel.add(JScrollPane(changlogs), BorderLayout.CENTER)
        panel.add(panel2, BorderLayout.SOUTH)

        panel2.layout = BorderLayout(0, 0)
        panel2.add(closeButton, BorderLayout.EAST)

        changlogs.isEditable = false
        changlogs.text = testText
        changlogs.lineWrap = true
        closeButton.addActionListener { close() }

        threadLock.start()
    }

    fun setAutoClose(time: Int)
    {
        autoCloseDelay = time
        autoCloseThread.start()
    }

    fun close()
    {
        window.dispose()
    }

    fun waitForClose()
    {
        threadLock.join()
    }

    /**
     * 列表文字
     */
    var contentText: String
        get() = changlogs.text
        set(value) = run { changlogs.text = value }

    /**
     * 标题栏文字
     */
    var titleText: String
        get() = window.title
        set(value) = run { window.title = value }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
//            SetupSwing.init()

            ChangeLogs()
//                .setAutoClose(1000)
        }
    }
}