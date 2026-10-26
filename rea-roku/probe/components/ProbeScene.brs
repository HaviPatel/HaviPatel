sub init()
    m.body = m.top.FindNode("body")
    m.top.SetFocus(true)
end sub

sub onText()
    m.body.text = m.top.text
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if press and key = "OK" then
        m.top.request = "launch"
        return true
    end if
    return false
end function
