import unittest
import unit

class TestUnitJavaApplication(unit.TestUnitApplicationJava):

    def setUpClass():
        unit.TestUnit().check_modules('java')

    def test_java_application_cookies(self):
        self.load('cookies')

        headers = self.get(headers={
            'Cookie': 'var1=val1; var2=val2',
            'Host': 'localhost',
            'Connection': 'close'
        })['headers']

        self.assertEqual(headers['X-Cookie-1'], 'val1', 'cookie 1')
        self.assertEqual(headers['X-Cookie-2'], 'val2', 'cookie 2')

    def test_java_application_filter(self):
        self.load('filter')

        headers = self.get()['headers']

        self.assertEqual(headers['X-Filter-Before'], '1', 'filter before')
        self.assertEqual(headers['X-Filter-After'], '1', 'filter after')

        self.assertEqual(self.get(url='/test')['headers']['X-Filter-After'],
            '0', 'filter after 2')

    def test_java_application_get_variables(self):
        self.load('get_params')

        headers = self.get(url='/?var1=val1&var2=')['headers']

        self.assertEqual(headers['X-Var-1'], 'val1', 'GET variables')
        self.assertEqual(headers['X-Var-2'], 'true', 'GET variables 2')
        self.assertEqual(headers['X-Var-3'], 'false', 'GET variables 3')

    def test_java_application_post_variables(self):
        self.load('post_params')

        headers = self.post(headers={
            'Content-Type': 'application/x-www-form-urlencoded',
            'Host': 'localhost',
            'Connection': 'close'
        }, body='var1=val1&var2=')['headers']

        self.assertEqual(headers['X-Var-1'], 'val1', 'POST variables')
        self.assertEqual(headers['X-Var-2'], 'true', 'POST variables 2')
        self.assertEqual(headers['X-Var-3'], 'false', 'POST variables 3')

    def test_java_application_session(self):
        self.load('session')

        headers = self.get(url='/?var1=val1')['headers']
        session_id = headers['X-Session-Id']

        self.assertEqual(headers['X-Var-1'], 'null', 'variable empty')
        self.assertEqual(headers['X-Session-New'], 'true', 'session create')

        headers = self.get(headers={
            'Host': 'localhost',
            'Cookie': 'JSESSIONID=' + session_id,
            'Connection': 'close'
        }, url='/?var1=val2')['headers']

        self.assertEqual(headers['X-Var-1'], 'val1', 'variable')
        self.assertEqual(headers['X-Session-New'], 'false', 'session resume')
        self.assertEqual(session_id, headers['X-Session-Id'], 'session same id')

    def test_java_application_session_listeners(self):
        self.load('session_listeners')

        headers = self.get(url='/test?var1=val1')['headers']
        session_id = headers['X-Session-Id']

        self.assertEqual(headers['X-Session-Created'], session_id,
            'session create')
        self.assertEqual(headers['X-Attr-Added'], 'var1=val1',
            'attribute add')

        headers = self.get(headers={
            'Host': 'localhost',
            'Cookie': 'JSESSIONID=' + session_id,
            'Connection': 'close'
        }, url='/?var1=val2')['headers']

        self.assertEqual(session_id, headers['X-Session-Id'], 'session same id')
        self.assertEqual(headers['X-Attr-Replaced'], 'var1=val1',
            'attribute replace')

        headers = self.get(headers={
            'Host': 'localhost',
            'Cookie': 'JSESSIONID=' + session_id,
            'Connection': 'close'
        }, url='/')['headers']

        self.assertEqual(session_id, headers['X-Session-Id'], 'session same id')
        self.assertEqual(headers['X-Attr-Removed'], 'var1=val2',
            'attribute remove')

if __name__ == '__main__':
    unittest.main()
