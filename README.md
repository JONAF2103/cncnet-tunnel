# Command & Conquer Red Alert 2 (Yuri's) Tunnel Server

#### This is a fork of https://github.com/CnCNet/cncnet-tunnel

Compile: ```gradle clean && gradle fatJar```

Basic Use: ```java -jar build/lib/cncnet-tunnel-v2-all-1.0-SNAPSHOT.jar```

Parameters:

 1. -name <str>          Custom name for the tunnel
 2. -maxclients <num>    Maximum number of ports to allocate
 3. -password <num>      Usage password (it's not supported yet)
 4. -port <num>          The port games are routed at
 5. -masterpw <str>      Optional password to send to master when registering
 6. -nomaster            Don't register to master
 7. -logfile <str>       Log everything to this file
 8. -headless            Don't start up the GUI
 9. -iplimit             Enable (currently too strict) hosting rate limit
 10. -maintpw <str>       Enable maintenance mode with password