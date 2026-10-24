"""Run with python3 tests/backend_smoke.py after mvn package; uses isolated storage."""
import http.client
import io
import json
from pathlib import Path
import socket
import subprocess
import tempfile
import time
import unittest
import zipfile
from urllib.parse import unquote


class BackendSmokeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.work = tempfile.TemporaryDirectory()
        cls.log = open(Path(cls.work.name) / 'server.log', 'w+')
        with socket.socket() as sock:
            sock.bind(('127.0.0.1', 0))
            cls.port = sock.getsockname()[1]
        config = dict(csrfSecret='smoke-test-secret-change-me', debugMediaDownloads=True,
                      purgeIntervalInSeconds=3600, host='127.0.0.1', port=cls.port)
        Path(cls.work.name, 'config.json').write_text(json.dumps(config))
        jar = Path(__file__).resolve().parents[1] / 'target/SpeechDrop-1.0-SNAPSHOT.jar'
        cls.server = subprocess.Popen(
            ['java', '-Dfile.encoding=UTF-8', '-jar', str(jar), '-conf', 'config.json'],
            cwd=cls.work.name, stdout=cls.log, stderr=subprocess.STDOUT)
        for _ in range(100):
            try:
                with socket.create_connection(('127.0.0.1', cls.port), timeout=.2):
                    return
            except OSError:
                if cls.server.poll() is not None:
                    break
                time.sleep(.1)
        cls.log.seek(0)
        cls.server.terminate()
        raise RuntimeError(cls.log.read())

    @classmethod
    def tearDownClass(cls):
        cls.server.terminate()
        cls.server.wait(timeout=10)
        cls.log.close()
        cls.work.cleanup()

    def request(self, method, path, body=None, headers=None):
        conn = http.client.HTTPConnection('127.0.0.1', self.port, timeout=10)
        headers = dict(headers or {})
        cookies = getattr(self, 'cookies', {})
        if cookies:
            headers['Cookie'] = '; '.join(key + '=' + value for key, value in cookies.items())
            if 'X-XSRF-TOKEN' in headers:
                headers['X-XSRF-TOKEN'] = unquote(cookies['XSRF-TOKEN'])
        conn.request(method, path, body, headers)
        response = conn.getresponse()
        for key, value in response.getheaders():
            if key.lower() == 'set-cookie':
                name, value = value.split(';')[0].split('=', 1)
                cookies[name] = value
        self.cookies = cookies
        headers = dict(response.getheaders())
        headers['set-cookie'] = '; '.join(
            value.split(';')[0] for key, value in response.getheaders()
            if key.lower() == 'set-cookie')
        result = response.status, headers, response.read()
        conn.close()
        return result

    def test_room_upload_download_delete_and_sockjs(self):
        status, headers, page = self.request('GET', '/')
        self.assertEqual(status, 200)
        self.assertIn(b'<html', page.lower())
        cookie = headers['set-cookie']
        token = unquote(self.cookies['XSRF-TOKEN'])
        auth = {'Cookie': cookie, 'X-XSRF-TOKEN': token,
                'Origin': 'http://127.0.0.1:' + str(self.port)}
        status, _, _ = self.request('POST', '/makeroom', 'name=blocked',
                                    {'Content-Type': 'application/x-www-form-urlencoded'})
        self.assertEqual(status, 403)
        status, headers, _ = self.request('POST', '/makeroom', 'name=Smoke',
            dict(auth, **{'Content-Type': 'application/x-www-form-urlencoded'}))
        self.assertEqual(status, 302)
        room = headers['location']
        self.assertEqual(self.request('GET', room)[0], 200)
        self.assertEqual(json.loads(self.request('GET', room + '/index')[2]), [])
        self.assertEqual(self.request('GET', '/sock/info')[0], 200)
        # SockJS XHR transport exercises the same event-bus bridge as the browser client.
        transport = '/sock/000/smoketest'
        self.assertEqual(self.request('POST', transport + '/xhr', b'', auth)[2].strip(), b'o')
        register = json.dumps([json.dumps({'type': 'register', 'address': 'speechdrop.room.' + room[1:]})])
        self.assertEqual(self.request('POST', transport + '/xhr_send', register,
            dict(auth, **{'Content-Type': 'application/json'}))[0], 204)
        initial = self.request('POST', transport + '/xhr', b'', auth)[2]
        self.assertIn(b'speechdrop.room.', initial)
        boundary = 'smoke-boundary'
        body = ('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; '
                'filename="sample.cmir"\r\nContent-Type: application/octet-stream\r\n\r\n'
                'test document\r\n--' + boundary + '--\r\n').encode()
        status, _, data = self.request('POST', room + '/upload', body,
            dict(auth, **{'Content-Type': 'multipart/form-data; boundary=' + boundary}))
        self.assertEqual(status, 200)
        self.assertEqual(json.loads(data)[0]['name'], 'sample.cmir')
        update = self.request('POST', transport + '/xhr', b'', auth)[2]
        self.assertIn(b'sample.cmir', update)
        self.assertEqual(self.request('GET', '/media/uploads' + room + '/0/sample.cmir')[2], b'test document')
        status, _, archive = self.request('GET', room + '/archive')
        self.assertEqual(status, 200)
        with zipfile.ZipFile(io.BytesIO(archive)) as zipped:
            self.assertEqual(zipped.read('sample.cmir'), b'test document')
        status, _, data = self.request('POST', room + '/delete', 'fileIndex=0',
            dict(auth, **{'Content-Type': 'application/x-www-form-urlencoded'}))
        self.assertEqual(status, 200)
        self.assertEqual(json.loads(data), [None])


if __name__ == '__main__':
    unittest.main()
