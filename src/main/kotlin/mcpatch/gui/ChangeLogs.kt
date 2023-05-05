package mcpatch.gui

import com.github.kasuminova.GUI.SetupSwing
import java.awt.BorderLayout
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*
import javax.swing.border.EmptyBorder

val cl = "外国政要有意密集来访，反映了中国所坚持的开放、协商、共赢的态度与发展方向，受到了世界的认可。\n" +
        "\n" +
        "受中国国务院总理李强邀请，新加坡总理李显龙27日正式开启为期六天的访华之旅。这是新冠肺炎疫情三年以来，李显龙首次访华。期间，他将到访广东广州、海南博鳌和北京三地。\n" +
        "\n" +
        "在李显龙“打头阵”后，中国或将迎来新一轮主场外交热潮。除了因肺炎而不得不推迟访华的巴西总统卢拉，西班牙首相桑切斯、法国总统马克龙、欧盟委员会主席冯德莱恩、意大利总理梅洛尼等欧洲政要也纷纷释放出寻求访华的信号。\n" +
        "\n" +
        "不远万里“排队”奔赴中国，他们究竟为何而来？\n" +
        "\n" +
        "李显龙此访有三点备受关注\n" +
        "\n" +
        "前不久，李显龙在新加坡接受了中国中央电视台的采访。访谈中，他直言中新“两国的关系非常好”，并表示“两国有互信，能够相互理解”。\n" +
        "\n" +
        "没过几天，新加坡总理办公室就发布重磅公告，李显龙将于3月27日至4月1日对中国进行为期六天的国事访问。\n" +
        "\n" +
        "公告显示，李显龙将在广东会见部分新加坡籍人士，在海南出席博鳌亚洲论坛2023年年会开幕式并发表讲话，在北京与中国领导人见面。\n" +
        "\n" +
        "李显龙此次访华之旅也备受媒体关注。“这是疫情发生以来，李显龙首次访华。”新加坡《联合早报》报道称，“在访问中国前，李显龙表示‘各国不得不承认，中国如今在全球舞台上正发挥着更大的作用’。”"

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
        changlogs.text = cl
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
            SetupSwing.init()

            ChangeLogs()
//                .setAutoClose(1000)
        }
    }
}