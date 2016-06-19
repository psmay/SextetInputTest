## Usage

    java -jar SextetInputTest.jar [mode=MODE] \
        [host=ADDRESS] [port=PORTNUMBER] \
        [interval=MILLISECONDS]

## Examples

    java -jar SextetInputTest.jar
                    # Open in stdout mode (implied by
                    # omission of PORT)

    java -jar SextetInputTest.jar port=6761
                    # Open in tcp mode (implied by presence of
                    # PORT), listen for client on port
                    # 6761 on any local address

    java -jar SextetInputTest.jar host=localhost port=6761
                    # Same, except only listen on
                    # localhost

## Parameters

The order of parameters is not important.

`mode=MODE`
:   (`stdout` or `tcp`; default is `tcp` if *port* is present or
    `stdout` otherwise) Determines whether output goes to standard
    output or to a TCP connection accepted on *port*. This setting is
    strictly optional; the presence or absence of *port* implies the
    *mode* setting.

`host=ADDRESS`
:   (tcp mode only; default is all local addresses) Sets the address on
    which the tcp-mode service accepts a connection.

`port=PORTNUMBER`
:   (tcp mode only; 0 .. 65535; no default) Sets the port on which the
    tcp-mode service accepts a connection.

`interval=MILLISECONDS`
:   (default 1000, meaning 1 second) Sets the interval, in milliseconds,
    of a watchdog timer that forces the output of a blank packet if the
    output remains idle for that amount of time. A non-positive value
    disables the timer, but doing this is discouraged: This watchdog
    timer's behavior serves partly as a keepalive for the connection,
    should one be necessary, partly to allow the tcp mode to detect a
    disconnect (by way of a failed write) without receiving actual
    input, and partly to allow the receiving end of the connection,
    which unfortunately might need to be implemented based on an
    uninterruptible blocking read, to allow it to recheck its loop
    variables with a higher frequency, resolving some situations that
    would otherwise hang the receiver.


