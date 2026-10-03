sub Main(args as dynamic)
    screen = createObject("roSGScreen")
    port = createObject("roMessagePort")
    screen.setMessagePort(port)
    scene = screen.createScene("RyzodScene")
    scene.observeField("exitRequested",port)
    screen.show()
    scene.setFocus(true)
    print "RYZOD Roku 0.1.0 started"
    while true
        msg = wait(0,port)
        if type(msg) = "roSGNodeEvent"
            if msg.getField() = "exitRequested"
                screen.close()
                return
            end if
        end if
        if type(msg) = "roSGScreenEvent"
            if msg.isScreenClosed() then return
        end if
    end while
end sub
