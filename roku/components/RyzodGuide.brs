sub init()
    m.body = m.top.findNode("body")
    m.row = 0
    m.cell = 0
    m.toolbar = -1
    m.top.channels = []
    m.top.epg = {}
end sub

sub setChannels()
    if m.body = invalid then return
    if not IsList(m.top.channels) then return
    if m.row >= m.top.channels.count() then m.row = 0
    m.cell = 0
    m.toolbar = -1
    drawGuide()
end sub

sub restoreSelection()
    m.row = m.top.selectedIndex
    if not IsList(m.top.channels) then return
    if m.row >= m.top.channels.count() then m.row = 0
    if m.row < 0 then m.row = 0
    m.toolbar = -1
    m.cell = 0
    drawGuide()
end sub

function visibleChannels() as object
    result = []
    if not IsList(m.top.channels) then return result
    start = int(m.row/6)*6
    for i = start to start+5
        if i < m.top.channels.count() then result.push(m.top.channels[i])
    end for
    return result
end function

function rowCells(channel as object) as object
    entries = []
    if IsMap(m.top.epg)
        if IsList(m.top.epg[channel.id]) then entries = m.top.epg[channel.id]
    end if
    return BuildGuideCells(entries,m.top.windowStart,m.top.windowStart+10800)
end function

sub drawGuide()
    if m.body = invalid then return
    ClearGroup(m.body)
    if not IsList(m.top.channels) then return
    for i = 0 to 2
        text = ["Categories","Update guide","Picture"][i]
        UiButton(m.body,text,i*210,0,198,42,m.toolbar=i and m.top.hasFocus())
    end for
    UiLabel(m.body,"CHANNEL",10,51,185,37,17,"0xBFEFFFFF")
    for i = 0 to 5
        UiLabel(m.body,ClockText(m.top.windowStart+i*1800),205+i*122,51,122,37,16,"0xBFEFFFFF")
    end for
    UiBox(m.body,0,92,940,2,"0xFFFFFFFF")
    UiBox(m.body,197,92,2,420,"0xFFFFFFFF")
    if m.top.channels.count() = 0
        UiLabel(m.body,"No channels in this category. Choose Categories or refresh.",10,155,930,100,23)
        return
    end if
    first = int(m.row/6)*6
    for i = first to first+5
        if i < m.top.channels.count()
            y = 100+(i-first)*66
            channel = m.top.channels[i]
            color = "0xF2F3F5FF"
            if i = m.row then color = "0x39FF88FF"
            UiLabel(m.body,channel.title,7,y,185,58,18,color)
            cells = rowCells(channel)
            if i = m.row and m.cell >= cells.count() then m.cell = cells.count()-1
            for j = 0 to cells.count()-1
                cell = cells[j]
                x = 205 + (cell.start-m.top.windowStart)/10800*730
                width = (cell.end-cell.start)/10800*730
                focused = i = m.row and j = m.cell and m.toolbar=-1 and m.top.hasFocus()
                UiButton(m.body,cell.title,x,y,width-2,58,focused)
            end for
            UiBox(m.body,0,y+62,940,1,"0x29445FFF")
        end if
    end for
    if m.row < m.top.channels.count()
        cells = rowCells(m.top.channels[m.row])
        if cells.count() > 0
            cell = cells[m.cell]
            description = ClockText(cell.start)+"–"+ClockText(cell.end)+"  "+cell.title+"  "+cell.desc
            label = UiLabel(m.body,description,6,493,930,28,16,"0xBFEFFFFF")
            label.wrap = false
            label.numLines = 1
        end if
    end if
end sub

function onKeyEvent(key as string, press as boolean) as boolean
    if not press then return false
    if key = "back"
        m.top.command = "rail"
        return true
    end if
    if m.toolbar >= 0
        if key = "left"
            if m.toolbar = 0
                m.top.command = "rail"
                return true
            end if
            m.toolbar = m.toolbar-1
        else if key = "right"
            m.toolbar = WrapIndex(m.toolbar+1,3)
        else if key = "down"
            m.toolbar = -1
        else if key = "OK"
            m.top.command = ["categories","refresh","preview"][m.toolbar]
            return true
        else if key = "up"
            return true
        else
            return false
        end if
    else
        count = m.top.channels.count()
        if count = 0
            m.toolbar = 0
        else
            if key = "up"
                if m.row = 0
                    m.toolbar = 0
                else
                    m.row = WrapIndex(m.row-1,count)
                    m.cell = 0
                end if
            else if key = "down"
                m.row = WrapIndex(m.row+1,count)
                m.cell = 0
            else if key = "left"
                if m.cell = 0
                    if m.top.windowStart > NowSeconds()-1800
                        m.top.command = "rail"
                        return true
                    else
                        m.top.windowStart = m.top.windowStart-10800
                    end if
                else
                    m.cell = m.cell-1
                end if
            else if key = "right"
                cells = rowCells(m.top.channels[m.row])
                if m.cell >= cells.count()-1
                    if m.top.windowStart < NowSeconds()+604800-10800 then m.top.windowStart = m.top.windowStart+10800
                    m.cell = 0
                else
                    m.cell = m.cell+1
                end if
            else if key = "OK" or key = "options"
                cells = rowCells(m.top.channels[m.row])
                m.top.selected = {channel:m.top.channels[m.row],program:cells[m.cell],index:m.row}
                return true
            else if key = "play"
                cells = rowCells(m.top.channels[m.row])
                m.top.selected = {channel:m.top.channels[m.row],program:cells[m.cell],index:m.row,watch:true}
                return true
            else
                return false
            end if
            m.top.selectionChanged = m.row
        end if
    end if
    drawGuide()
    return true
end function
