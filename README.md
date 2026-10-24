SpeechDrop
==

Fast, efficient, and secure document sharing for debate rounds.

Screenshots
-- 

![Home Screen](https://i.imgur.com/VxTlwsS.png)
![Document upload and Room view](https://i.imgur.com/2q0zyTc.png)


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

Build tools
--

Use a current Maven 3.x installation from your package manager. Maven installs Node 22.19.0 and uses Corepack to run Yarn 4.18.1, pinned in `frontend/package.json`. The frontend uses the `node-modules` linker.

For frontend-only work, use Node 22.19.0 with Corepack enabled, then run `yarn install --immutable` and `yarn build` in `frontend/`. After changing dependencies, run `yarn install` and commit the updated `yarn.lock`. To audit all frontend dependencies, run `yarn npm audit --all --recursive`.
