sub init()
    m.body = m.top.findNode("body")
    m.index = 0
    m.page = 0
end sub

sub openKeyboard()
    if not m.top.opened then return
    m.index = 0
    m.page = 0
    m.original = m.top.value
    m.top.visible = true
    m.top.setFocus(true)
    drawKeyboard()
end sub

sub drawKeyboard()
    ClearGroup(m.body)
    UiBox(m.body,0,0,1280,720,"0x07111FFF")
    UiPoster(m.body,"pkg:/images/brand.png",60,30,180,90)
    UiLabel(m.body,m.top.title,265,40,880,60,30)
    displayed = m.top.value
    if m.top.password then displayed = string(len(displayed),"*")
    UiBox(m.body,172,142,936,64,"0xF5B944FF")
    UiBox(m.body,176,146,928,56,"0x101C2DFF")
    UiLabel(m.body,displayed,190,150,900,48,24)
    keys = KeyboardPages()[m.page]
    keys.append(["SPACE","DELETE","CLEAR","PAGE","DONE","CANCEL","","","",""])
    for i = 0 to keys.count()-1
        x = 172 + (i mod 10)*94
        y = 234 + int(i/10)*72
        UiButton(m.body,keys[i],x,y,86,60,i=m.index)
    end for
    UiLabel(m.body,"Page "+(m.page+1).toStr()+" of 3  •  * changes page  •  Replay deletes  •  Back cancels",172,615,950,48,18,"0xBFEFFFFF")
end sub

function onKeyEvent(key as string, press as boolean) as boolean
    if not press then return false
    if key = "back"
        m.top.value = m.original
        m.top.visible = false
        m.top.cancelled = true
        return true
    end if
    if key = "options"
        m.page = WrapIndex(m.page+1,3)
    else if key = "replay"
        removeLetter()
    else if key = "left" or key = "right" or key = "up" or key = "down"
        m.index = KeyboardMove(m.index,key,10,50)
    else if key = "OK"
        if m.index < 40
            if len(m.top.value) < 2048 then m.top.value = m.top.value + KeyboardPages()[m.page][m.index]
        else if m.index = 40
            m.top.value = m.top.value + " "
        else if m.index = 41
            removeLetter()
        else if m.index = 42
            m.top.value = ""
        else if m.index = 43
            m.page = WrapIndex(m.page+1,3)
        else if m.index = 44
            m.top.visible = false
            m.top.done = true
            return true
        else if m.index = 45
            m.top.value = m.original
            m.top.visible = false
            m.top.cancelled = true
            return true
        end if
    else if left(key,4) = "Lit_"
        if len(m.top.value) < 2048 then m.top.value = m.top.value + mid(key,5)
    else
        return false
    end if
    drawKeyboard()
    return true
end function

sub removeLetter()
    if len(m.top.value) > 0 then m.top.value = left(m.top.value,len(m.top.value)-1)
end sub
