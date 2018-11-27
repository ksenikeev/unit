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

if __name__ == '__main__':
    unittest.main()
