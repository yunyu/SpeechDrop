SpeechDrop
==

Fast, efficient, and secure document sharing for debate rounds.

Screenshots
-- 
![Home Screen](https://github.com/Jflick58/SpeechDrop/blob/master/Capture.PNG)
![Document upload and Room view](https://github.com/Jflick58/SpeechDrop/blob/master/Capture2.PNG?raw=true)

Running locally
--

1. Clone this repository
2. Run `mvn clean package`
3. Run `java -Dfile.encoding=UTF-8 -jar target/SpeechDrop-1.0-SNAPSHOT.jar -conf config.example.json`
4. Visit `http://localhost:6901/`

Production setup
--

1. Clone and build, as described previously 
2. Copy `config.example.json` to `config.json`
3. Set `debugMediaDownloads` to false
4. Set `mediaUrl` to the root directory of where your static files are served
5. Set `csrfSecret` to a randomly generated string
6. Configure `host` and `port` if needed
7. Set up your preferred webserver to handle proxying, static serving, and socket upgrades (see `nginx` folder for example)
8. Copy JAR to destination directory and switch to it
9. Run `java -Dfile.encoding=UTF-8 -jar SpeechDrop-1.0-SNAPSHOT.jar -conf config.json`
10. Visit site

To fix
--

The JS minification is literally copy pasting at the moment, should be fixed.
