' REA Probe: finds out, on this TV, whether an app may control the TV through Roku's External
' Control Protocol (ECP), which an Apple TV-style home screen would need to list and open apps.
' Roku's docs say apps can no longer send ECP; this checks what Roku OS really does here.
' Nothing on the TV is changed. The results show on screen and on the debug console (port 8085).
sub Main()
    screen = CreateObject("roSGScreen")
    port = CreateObject("roMessagePort")
    screen.SetMessagePort(port)
    scene = screen.CreateScene("ProbeScene")
    screen.Show()
    scene.ObserveField("request", port)

    base = "http://" + LocalIp() + ":8060"
    lines = ["REA probe  ·  " + base, ""]
    lines.Push(Check("GET", base + "/query/device-info", "Read the TV's details"))
    lines.Push(Check("GET", base + "/query/apps", "List installed apps"))
    lines.Push(Check("GET", base + "/query/active-app", "See what's on screen"))
    lines.Push("")
    lines.Push("Press OK to try opening Netflix from this app. Press Back to leave.")
    Show(scene, lines)

    while true
        msg = wait(0, port)
        if type(msg) = "roSGScreenEvent" and msg.IsScreenClosed() then return
        if type(msg) = "roSGNodeEvent" and msg.GetField() = "request" and msg.GetData() = "launch" then
            lines.Push(Check("POST", base + "/launch/12", "Open Netflix"))
            Show(scene, lines)
        end if
    end while
end sub

sub Show(scene as Object, lines as Object)
    text = lines.Join(Chr(10))
    scene.text = text
    print text
end sub

' One ECP request, summarised as a line: what was tried, and whether Roku allowed it.
function Check(method as String, url as String, what as String) as String
    r = Request(method, url)
    if r.code >= 200 and r.code < 300 then
        detail = "HTTP " + r.code.ToStr()
        apps = Count(r.body, "<app ")
        if apps > 0 then detail = detail + ", " + apps.ToStr() + " apps"
        return "OK      " + what + "  (" + detail + ")"
    end if
    reason = r.reason
    if r.code > 0 then reason = "HTTP " + r.code.ToStr() + " " + reason
    return "BLOCKED " + what + "  (" + reason + ")"
end function

function Request(method as String, url as String) as Object
    transfer = CreateObject("roUrlTransfer")
    port = CreateObject("roMessagePort")
    transfer.SetMessagePort(port)
    transfer.SetUrl(url)
    transfer.RetainBodyOnError(true)
    if method = "POST" then
        transfer.SetRequest("POST")
        started = transfer.AsyncPostFromString("")
    else
        started = transfer.AsyncGetToString()
    end if
    if not started then return { code: -1, body: "", reason: "could not start the request" }
    msg = wait(5000, port)
    if type(msg) = "roUrlEvent" then
        return { code: msg.GetResponseCode(), body: msg.GetString(), reason: msg.GetFailureReason() }
    end if
    transfer.AsyncCancel()
    return { code: 0, body: "", reason: "no answer in 5 s" }
end function

function Count(text as String, needle as String) as Integer
    n = 0
    at = Instr(1, text, needle)
    while at > 0
        n = n + 1
        at = Instr(at + Len(needle), text, needle)
    end while
    return n
end function

function LocalIp() as String
    ips = CreateObject("roDeviceInfo").GetIPAddrs()
    for each name in ips
        return ips[name]
    end for
    return "127.0.0.1"
end function
