import unittest
import unit

class TestUnitJavaApplication(unit.TestUnitApplicationJava):

    def setUpClass():
        u = unit.TestUnit()

        u.check_modules('java')

    def test_go_application_cookies(self):
        self.load('cookies')

        resp = self.get(headers={
            'Cookie': 'var1=val1; var2=val2',
            'Host': 'localhost',
            'Connection': 'close'
        })

        self.assertEqual(resp['headers']['X-Cookie-1'], 'val1', 'cookie 1')
        self.assertEqual(resp['headers']['X-Cookie-2'], 'val2', 'cookie 2')

if __name__ == '__main__':
    unittest.main()
