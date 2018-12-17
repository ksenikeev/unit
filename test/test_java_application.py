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

    def test_java_application_jsp(self):
        self.load('jsp')

        headers = self.get(headers={
            'Host': 'localhost',
            'Connection': 'close'
        }, url='/index.jsp')['headers']

        self.assertEqual(headers['X-Unit-JSP'], 'ok', 'JSP Ok header')

    def test_java_application_url_pattern(self):
        self.load('url_pattern')

        headers = self.get(headers={
            'Host': 'localhost',
            'Connection': 'close'
        }, url='/foo/bar/index.html')['headers']

        self.assertEqual(headers['X-Id'], 'servlet1', '#1 Servlet1 request')
        self.assertEqual(headers['X-Request-URI'], '/foo/bar/index.html', '#1 request URI')
        self.assertEqual(headers['X-Servlet-Path'], '/foo/bar', '#1 servlet path')
        self.assertEqual(headers['X-Path-Info'], '/index.html', '#1 path info')


        headers = self.get(headers={
            'Host': 'localhost',
            'Connection': 'close'
        }, url='/foo/bar/index.bop')['headers']

        self.assertEqual(headers['X-Id'], 'servlet1', '#2 Servlet1 request')
        self.assertEqual(headers['X-Request-URI'], '/foo/bar/index.bop', '#2 request URI')
        self.assertEqual(headers['X-Servlet-Path'], '/foo/bar', '#2 servlet path')
        self.assertEqual(headers['X-Path-Info'], '/index.bop', '#2 path info')


        headers = self.get(headers={
            'Host': 'localhost',
            'Connection': 'close'
        }, url='/baz')['headers']

        self.assertEqual(headers['X-Id'], 'servlet2', '#3 Servlet2 request')
        self.assertEqual(headers['X-Request-URI'], '/baz', '#3 request URI')
        self.assertEqual(headers['X-Servlet-Path'], '/baz', '#3 servlet path')
        self.assertEqual(headers['X-Path-Info'], 'null', '#3 path info')


        headers = self.get(headers={
            'Host': 'localhost',
            'Connection': 'close'
        }, url='/baz/index.html')['headers']

        self.assertEqual(headers['X-Id'], 'servlet2', '#4 Servlet2 request')
        self.assertEqual(headers['X-Request-URI'], '/baz/index.html', '#4 request URI')
        self.assertEqual(headers['X-Servlet-Path'], '/baz', '#4 servlet path')
        self.assertEqual(headers['X-Path-Info'], '/index.html', '#4 path info')


        headers = self.get(headers={
            'Host': 'localhost',
            'Connection': 'close'
        }, url='/catalog')['headers']

        self.assertEqual(headers['X-Id'], 'servlet3', '#5 Servlet3 request')
        self.assertEqual(headers['X-Request-URI'], '/catalog', '#5 request URI')
        self.assertEqual(headers['X-Servlet-Path'], '/catalog', '#5 servlet path')
        self.assertEqual(headers['X-Path-Info'], 'null', '#5 path info')


        headers = self.get(headers={
            'Host': 'localhost',
            'Connection': 'close'
        }, url='/catalog/index.html')['headers']

        self.assertEqual(headers['X-Id'], 'default', '#6 default request')
        self.assertEqual(headers['X-Request-URI'], '/catalog/index.html', '#6 request URI')
        self.assertEqual(headers['X-Servlet-Path'], '/catalog/index.html', '#6 servlet path')
        self.assertEqual(headers['X-Path-Info'], 'null', '#6 path info')


        headers = self.get(headers={
            'Host': 'localhost',
            'Connection': 'close'
        }, url='/catalog/racecar.bop')['headers']

        self.assertEqual(headers['X-Id'], 'servlet4', '#7 servlet4 request')
        self.assertEqual(headers['X-Request-URI'], '/catalog/racecar.bop', '#7 request URI')
        self.assertEqual(headers['X-Servlet-Path'], '/catalog/racecar.bop', '#7 servlet path')
        self.assertEqual(headers['X-Path-Info'], 'null', '#7 path info')


        headers = self.get(headers={
            'Host': 'localhost',
            'Connection': 'close'
        }, url='/index.bop')['headers']

        self.assertEqual(headers['X-Id'], 'servlet4', '#8 servlet4 request')
        self.assertEqual(headers['X-Request-URI'], '/index.bop', '#8 request URI')
        self.assertEqual(headers['X-Servlet-Path'], '/index.bop', '#8 servlet path')
        self.assertEqual(headers['X-Path-Info'], 'null', '#8 path info')


        headers = self.get(headers={
            'Host': 'localhost',
            'Connection': 'close'
        }, url='/foo/baz')['headers']

        self.assertEqual(headers['X-Id'], 'servlet0', '#9 servlet0 request')
        self.assertEqual(headers['X-Request-URI'], '/foo/baz', '#9 request URI')
        self.assertEqual(headers['X-Servlet-Path'], '/foo', '#9 servlet path')
        self.assertEqual(headers['X-Path-Info'], '/baz', '#9 path info')

    def test_java_application_header(self):
        self.load('header')

        headers = self.get(headers={
            'Host': 'localhost',
            'Connection': 'close'
        }, url='/')['headers']

        self.assertEqual(headers['X-Set-Utf8-Value'], '????', 'set Utf8 header value')
        self.assertEqual(headers['X-Set-Utf8-Name-???'], 'x', 'set Utf8 header name')
        self.assertEqual(headers['X-Add-Utf8-Value'], '????', 'add Utf8 header value')
        self.assertEqual(headers['X-Add-Utf8-Name-???'], 'y', 'add Utf8 header name')
        self.assertEqual(headers['X-Add-Test'], 'v1', 'add null header')
        self.assertEqual('X-Set-Test1' in headers, False, 'set null header')
        self.assertEqual(headers['X-Set-Test2'], '', 'set empty header')

    def test_java_application_content_type(self):
        self.load('content_type')

        headers = self.get(headers={
            'Host': 'localhost',
            'Connection': 'close'
        }, url='/1')['headers']

        self.assertEqual(headers['Content-Type'], 'text/plain;charset=utf-8', '#1 Content-Type header')
        self.assertEqual(headers['X-Content-Type'], 'text/plain;charset=utf-8', '#1 response Content-Type')
        self.assertEqual(headers['X-Character-Encoding'], 'utf-8', '#1 response charset')


        headers = self.get(headers={
            'Host': 'localhost',
            'Connection': 'close'
        }, url='/2')['headers']

        self.assertEqual(headers['Content-Type'], 'text/plain;charset=iso-8859-1', '#2 Content-Type header')
        self.assertEqual(headers['X-Content-Type'], 'text/plain;charset=iso-8859-1', '#2 response Content-Type')
        self.assertEqual(headers['X-Character-Encoding'], 'iso-8859-1', '#2 response charset')


        headers = self.get(headers={
            'Host': 'localhost',
            'Connection': 'close'
        }, url='/3')['headers']

        self.assertEqual(headers['Content-Type'], 'text/plain;charset=windows-1251', '#3 Content-Type header')
        self.assertEqual(headers['X-Content-Type'], 'text/plain;charset=windows-1251', '#3 response Content-Type')
        self.assertEqual(headers['X-Character-Encoding'], 'windows-1251', '#3 response charset')


        headers = self.get(headers={
            'Host': 'localhost',
            'Connection': 'close'
        }, url='/4')['headers']

        self.assertEqual(headers['Content-Type'], 'text/plain;charset=windows-1251', '#4 Content-Type header')
        self.assertEqual(headers['X-Content-Type'], 'text/plain;charset=windows-1251', '#4 response Content-Type')
        self.assertEqual(headers['X-Character-Encoding'], 'windows-1251', '#4 response charset')


        headers = self.get(headers={
            'Host': 'localhost',
            'Connection': 'close'
        }, url='/5')['headers']

        self.assertEqual(headers['Content-Type'], 'text/plain;charset=iso-8859-1', '#5 Content-Type header')
        self.assertEqual(headers['X-Content-Type'], 'text/plain;charset=iso-8859-1', '#5 response Content-Type')
        self.assertEqual(headers['X-Character-Encoding'], 'iso-8859-1', '#5 response charset')


        headers = self.get(headers={
            'Host': 'localhost',
            'Connection': 'close'
        }, url='/6')['headers']

        self.assertEqual('Content-Type' in headers, False, '#6 no Content-Type header')
        self.assertEqual('X-Content-Type' in headers, False, '#6 no response Content-Type')
        self.assertEqual(headers['X-Character-Encoding'], 'utf-8', '#6 response charset')


        headers = self.get(headers={
            'Host': 'localhost',
            'Connection': 'close'
        }, url='/7')['headers']

        self.assertEqual(headers['Content-Type'], 'text/plain;charset=utf-8', '#7 Content-Type header')
        self.assertEqual(headers['X-Content-Type'], 'text/plain;charset=utf-8', '#7 response Content-Type')
        self.assertEqual(headers['X-Character-Encoding'], 'utf-8', '#7 response charset')


        headers = self.get(headers={
            'Host': 'localhost',
            'Connection': 'close'
        }, url='/8')['headers']

        self.assertEqual(headers['Content-Type'], 'text/html;charset=utf-8', '#8 Content-Type header')
        self.assertEqual(headers['X-Content-Type'], 'text/html;charset=utf-8', '#8 response Content-Type')
        self.assertEqual(headers['X-Character-Encoding'], 'utf-8', '#8 response charset')

    def test_java_application_welcome_files(self):
        self.load('welcome_files')

        headers = self.get(headers={
            'Host': 'localhost',
            'Connection': 'close'
        }, url='/')['headers']


        resp = self.get(headers={
            'Host': 'localhost',
            'Connection': 'close'
        }, url='/dir1')

        self.assertEqual(resp['status'], 302, 'dir redirect expected')


        resp = self.get(headers={
            'Host': 'localhost',
            'Connection': 'close'
        }, url='/dir1/')

        self.assertEqual('This is index.txt.' in resp['body'], True, 'dir1 index body')


        headers = self.get(headers={
            'Host': 'localhost',
            'Connection': 'close'
        }, url='/dir2/')['headers']

        self.assertEqual(headers['X-Unit-JSP'], 'ok', 'JSP Ok header')


        headers = self.get(headers={
            'Host': 'localhost',
            'Connection': 'close'
        }, url='/dir3/')['headers']

        self.assertEqual(headers['X-App-Servlet'], '1', 'URL pattern overrides welcome file')


        headers = self.get(headers={
            'Host': 'localhost',
            'Connection': 'close'
        }, url='/dir4/')['headers']

        self.assertEqual('X-App-Servlet' in headers, False, 'Static welcome file served first')


        headers = self.get(headers={
            'Host': 'localhost',
            'Connection': 'close'
        }, url='/dir5/')['headers']

        self.assertEqual(headers['X-App-Servlet'], '1', 'Servlet for welcome file served when no static file found')

    def test_java_application_request_listeners(self):
        self.load('request_listeners')

        headers = self.get(url='/test1')['headers']

        self.assertEqual(headers['X-Request-Initialized'], '/test1',
            'request initialized event')
        self.assertEqual(headers['X-Request-Destroyed'], '',
            'request destroyed event')
        self.assertEqual(headers['X-Attr-Added'], '',
            'attribute added event')
        self.assertEqual(headers['X-Attr-Removed'], '',
            'attribute removed event')
        self.assertEqual(headers['X-Attr-Replaced'], '',
            'attribute replaced event')


        headers = self.get(url='/test2?var1=1')['headers']

        self.assertEqual(headers['X-Request-Initialized'], '/test2',
            'request initialized event')
        self.assertEqual(headers['X-Request-Destroyed'], '/test1',
            'request destroyed event')
        self.assertEqual(headers['X-Attr-Added'], 'var=1;',
            'attribute added event')
        self.assertEqual(headers['X-Attr-Removed'], 'var=1;',
            'attribute removed event')
        self.assertEqual(headers['X-Attr-Replaced'], '',
            'attribute replaced event')


        headers = self.get(url='/test3?var1=1&var2=2')['headers']

        self.assertEqual(headers['X-Request-Initialized'], '/test3',
            'request initialized event')
        self.assertEqual(headers['X-Request-Destroyed'], '/test2',
            'request destroyed event')
        self.assertEqual(headers['X-Attr-Added'], 'var=1;',
            'attribute added event')
        self.assertEqual(headers['X-Attr-Removed'], 'var=2;',
            'attribute removed event')
        self.assertEqual(headers['X-Attr-Replaced'], 'var=1;',
            'attribute replaced event')


        headers = self.get(url='/test4?var1=1&var2=2&var3=3')['headers']

        self.assertEqual(headers['X-Request-Initialized'], '/test4',
            'request initialized event')
        self.assertEqual(headers['X-Request-Destroyed'], '/test3',
            'request destroyed event')
        self.assertEqual(headers['X-Attr-Added'], 'var=1;',
            'attribute added event')
        self.assertEqual(headers['X-Attr-Removed'], '',
            'attribute removed event')
        self.assertEqual(headers['X-Attr-Replaced'], 'var=1;var=2;',
            'attribute replaced event')

    def test_java_application_request_uri_forward(self):
        self.load('forward')

        resp = self.get(url='/fwd?uri=/data/test')
        headers = resp['headers']

        self.assertEqual(headers['X-REQUEST-Id'], 'fwd',
            'initial request servlet mapping')
        self.assertEqual(headers['X-Forward-To'], '/data/test',
            'forwarding triggered')

        self.assertEqual(headers['X-FORWARD-Id'], 'data',
            'forward request servlet mapping')
        self.assertEqual(headers['X-FORWARD-Request-URI'], '/data/test',
            'forward request uri')
        self.assertEqual(headers['X-FORWARD-Servlet-Path'], '/data',
            'forward request servlet path')
        self.assertEqual(headers['X-FORWARD-Path-Info'], '/test',
            'forward request path info')
        self.assertEqual(headers['X-FORWARD-Query-String'], 'uri=/data/test',
            'forward request query string')

        self.assertEqual(headers['X-javax.servlet.forward.request_uri'], '/fwd',
            'original request uri')
        self.assertEqual(headers['X-javax.servlet.forward.context_path'], '',
            'original request context path')
        self.assertEqual(headers['X-javax.servlet.forward.servlet_path'], '/fwd',
            'original request servlet path')
        self.assertEqual(headers['X-javax.servlet.forward.path_info'], 'null',
            'original request path info')
        self.assertEqual(headers['X-javax.servlet.forward.query_string'], 'uri=/data/test',
            'original request query')

        self.assertEqual('Before forwarding' in resp['body'], False,
            'discarded data added before forward() call')
        self.assertEqual('X-After-Forwarding' in headers, False,
            'cannot add headers after forward() call')
        self.assertEqual('After forwarding' in resp['body'], False,
            'cannot add data after forward() call')

    def test_java_application_named_dispatcher_forward(self):
        self.load('forward')

        resp = self.get(url='/fwd?disp=name&uri=data')
        headers = resp['headers']

        self.assertEqual(headers['X-REQUEST-Id'], 'fwd',
            'initial request servlet mapping')
        self.assertEqual(headers['X-Forward-To'], 'data',
            'forwarding triggered')

        self.assertEqual(headers['X-FORWARD-Id'], 'data',
            'forward request servlet mapping')
        self.assertEqual(headers['X-FORWARD-Request-URI'], '/fwd',
            'forward request uri')
        self.assertEqual(headers['X-FORWARD-Servlet-Path'], '/fwd',
            'forward request servlet path')
        self.assertEqual(headers['X-FORWARD-Path-Info'], 'null',
            'forward request path info')
        self.assertEqual(headers['X-FORWARD-Query-String'], 'disp=name&uri=data',
            'forward request query string')

        self.assertEqual(headers['X-javax.servlet.forward.request_uri'], 'null',
            'original request uri')
        self.assertEqual(headers['X-javax.servlet.forward.context_path'], 'null',
            'original request context path')
        self.assertEqual(headers['X-javax.servlet.forward.servlet_path'], 'null',
            'original request servlet path')
        self.assertEqual(headers['X-javax.servlet.forward.path_info'], 'null',
            'original request path info')
        self.assertEqual(headers['X-javax.servlet.forward.query_string'], 'null',
            'original request query')

        self.assertEqual('Before forwarding' in resp['body'], False,
            'discarded data added before forward() call')
        self.assertEqual('X-After-Forwarding' in headers, False,
            'cannot add headers after forward() call')
        self.assertEqual('After forwarding' in resp['body'], False,
            'cannot add data after forward() call')

    def test_java_application_request_uri_include(self):
        self.load('include')

        resp = self.get(url='/inc?uri=/data/test')
        headers = resp['headers']
        body = resp['body']

        self.assertEqual(headers['X-REQUEST-Id'], 'inc',
            'initial request servlet mapping')
        self.assertEqual(headers['X-Include'], '/data/test',
            'including triggered')

        self.assertEqual('X-INCLUDE-Id' in headers, False,
            'unable to add headers in include request')

        self.assertEqual('javax.servlet.include.request_uri:  /data/test' in body,
            True, 'include request uri')
#        self.assertEqual('javax.servlet.include.context_path: ' in body,
#            'include request context path')
        self.assertEqual('javax.servlet.include.servlet_path: /data' in body,
            True, 'include request servlet path')
        self.assertEqual('javax.servlet.include.path_info:    /test' in body,
            True, 'include request path info')
        self.assertEqual('javax.servlet.include.query_string: null' in body,
            True, 'include request query')

        self.assertEqual('Before include' in body, True,
            'preserve data added before include() call')
        self.assertEqual(headers['X-After-Include'], 'you-should-see-this',
            'add headers after include() call')
        self.assertEqual('After include' in body, True,
            'add data after forward() call')

    def test_java_application_named_dispatcher_include(self):
        self.load('include')

        resp = self.get(url='/inc?disp=name&uri=data')
        headers = resp['headers']
        body = resp['body']

        self.assertEqual(headers['X-REQUEST-Id'], 'inc',
            'initial request servlet mapping')
        self.assertEqual(headers['X-Include'], 'data',
            'including triggered')

        self.assertEqual('X-INCLUDE-Id' in headers, False,
            'unable to add headers in include request')

        self.assertEqual('javax.servlet.include.request_uri:  null' in body,
            True, 'include request uri')
#        self.assertEqual('javax.servlet.include.context_path: null' in body,
#            'include request context path')
        self.assertEqual('javax.servlet.include.servlet_path: null' in body,
            True, 'include request servlet path')
        self.assertEqual('javax.servlet.include.path_info:    null' in body,
            True, 'include request path info')
        self.assertEqual('javax.servlet.include.query_string: null' in body,
            True, 'include request query')

        self.assertEqual('Before include' in body, True,
            'preserve data added before include() call')
        self.assertEqual(headers['X-After-Include'], 'you-should-see-this',
            'add headers after include() call')
        self.assertEqual('After include' in body, True,
            'add data after forward() call')

    def test_java_application_path_translation(self):
        self.load('path_translation')

        headers = self.get(url='/pt/test?path=/')['headers']

        self.assertEqual(headers['X-Servlet-Path'], '/pt',
            'matched servlet path')
        self.assertEqual(headers['X-Path-Info'], '/test',
            'the rest of the path')
        self.assertEqual(headers['X-Path-Translated'],
            headers['X-Real-Path'] + headers['X-Path-Info'],
            'translated path is the app root + path info')
        self.assertEqual(
            headers['X-Resource-Paths'].endswith('/WEB-INF/, /index.html]'),
            True, 'app root directory content')
        self.assertEqual(headers['X-Resource-As-Stream'], 'null',
            'no resource stream for root path')


        headers = self.get(url='/test?path=/none')['headers']

        self.assertEqual(headers['X-Servlet-Path'], '/test',
            'matched whole path')
        self.assertEqual(headers['X-Path-Info'], 'null',
            'the rest of the path is null, whole path matched')
        self.assertEqual(headers['X-Path-Translated'], 'null',
            'translated path is null because path info is null')
        self.assertEqual(headers['X-Real-Path'].endswith('/none'), True,
            'read path is not null')
        self.assertEqual(headers['X-Resource-Paths'], 'null',
            'no resource found')
        self.assertEqual(headers['X-Resource-As-Stream'], 'null',
            'no resource stream')

if __name__ == '__main__':
    unittest.main()
