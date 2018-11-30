import unittest
import unit

class TestUnitJavaApplication(unit.TestUnitApplicationJava):

    def setUpClass():
        u = unit.TestUnit()

        u.check_modules('java')

    def test_java_application_cookies(self):
        self.load('cookies')

        resp = self.get(headers={
            'Cookie': 'var1=val1; var2=val2',
            'Host': 'localhost',
            'Connection': 'close'
        })

        self.assertEqual(resp['headers']['X-Cookie-1'], 'val1', 'cookie 1')
        self.assertEqual(resp['headers']['X-Cookie-2'], 'val2', 'cookie 2')

    def test_java_application_filter(self):
        self.load('filter')

        resp = self.get(headers={
            'Host': 'localhost',
            'Connection': 'close'
        })

        self.assertEqual(resp['headers']['X-Filter-Before'], '1', 'filter before')
        self.assertEqual(resp['headers']['X-Filter-After'], '1', 'filter after')

        resp = self.get(url="/test", headers={
            'Host': 'localhost',
            'Connection': 'close'
        })

        self.assertEqual(resp['headers']['X-Filter-After'], '0', 'filter after')

    def test_java_application_get_variables(self):
        self.load('get_params')

        resp = self.get(headers={
            'Host': 'localhost',
            'Connection': 'close'
        }, url='/?var1=val1&var2=')
        self.assertEqual(resp['headers']['X-Var-1'], 'val1', 'GET variables')
        self.assertEqual(resp['headers']['X-Var-2'], 'true', 'GET variables 2')
        self.assertEqual(resp['headers']['X-Var-3'], 'false', 'GET variables 3')

    def test_java_application_post_variables(self):
        self.load('post_params')

        resp = self.post(headers={
            'Content-Type': 'application/x-www-form-urlencoded',
            'Host': 'localhost',
            'Connection': 'close'
        }, body='var1=val1&var2=')
        self.assertEqual(resp['headers']['X-Var-1'], 'val1', 'POST variables')
        self.assertEqual(resp['headers']['X-Var-2'], 'true', 'POST variables 2')
        self.assertEqual(resp['headers']['X-Var-3'], 'false', 'POST variables 3')

    def test_java_application_session(self):
        self.load('session')

        resp = self.get(headers={
            'Host': 'localhost',
            'Connection': 'close'
        }, url='/?var1=val1')

        session_id = resp['headers']['X-Session-Id']

        self.assertEqual(resp['headers']['X-Var-1'], 'null', 'GET variables')
        self.assertEqual(resp['headers']['X-Session-New'], 'true', 'GET variables 2')

        resp = self.get(headers={
            'Host': 'localhost',
            'Cookie': 'JSESSIONID=' + session_id,
            'Connection': 'close'
        }, url='/?var1=val2')

        self.assertEqual(resp['headers']['X-Var-1'], 'val1', 'GET variables')
        self.assertEqual(resp['headers']['X-Session-New'], 'false', 'GET variables 2')

if __name__ == '__main__':
    unittest.main()
