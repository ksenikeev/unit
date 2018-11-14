package nginx.unit;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import java.lang.ClassLoader;
import java.lang.ClassNotFoundException;
import java.lang.IllegalArgumentException;
import java.lang.IllegalStateException;
import java.lang.reflect.Constructor;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;

import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.FilterRegistration.Dynamic;
import javax.servlet.FilterRegistration;
import javax.servlet.MultipartConfigElement;
import javax.servlet.Registration;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.ServletResponse;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.ServletRequestListener;
import javax.servlet.ServletSecurityElement;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.annotation.HandlesTypes;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.annotation.WebFilter;
import javax.servlet.descriptor.JspConfigDescriptor;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionIdListener;
import javax.servlet.http.HttpSessionListener;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.util.AttributesMap;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


public class Context extends AttributesMap implements ServletContext, InitParams
{
    public final static int SERVLET_MAJOR_VERSION = 3;
    public final static int SERVLET_MINOR_VERSION = 1;

    private String context_path_ = "";
    private String server_info_ = "unit";
    private String app_version_ = "";
    private MimeTypes mime_types_;
    private boolean metadata_complete_ = false;

    private ClassLoader loader_;
    private File extracted_dir_;

    private final Map<String, String> init_params_ = new HashMap<String, String>();

    private List<FilterReg> filters_ = new ArrayList<FilterReg>();
    private final Map<String, FilterReg> name2filter_ = new HashMap<>();

    private final List<ServletReg> servlets_ = new ArrayList<ServletReg>();
    private final Map<String, ServletReg> name2servlet_ = new HashMap<>();
    private final Map<String, ServletReg> pattern2servlet_ = new HashMap<>();
    private final Map<String, ServletReg> exact2servlet_ = new HashMap<>();
    private final List<PrefixPattern> prefix_patterns_ = new ArrayList<>();
    private final Map<String, ServletReg> suffix2servlet_ = new HashMap<>();
    private ServletReg default_servlet_;

    public static final Class<?>[] SERVLET_LISTENER_TYPES = new Class[] {
        ServletContextListener.class,
        ServletContextAttributeListener.class,
        ServletRequestListener.class,
        ServletRequestAttributeListener.class
    };

    private final List<ServletContextListener> _servletContextListeners = new ArrayList<>();
    private final List<ServletContextListener> _destroyServletContextListeners = new ArrayList<>();
    private final List<ServletContextAttributeListener> _servletContextAttributeListeners = new ArrayList<>();
    private final List<ServletRequestListener> _servletRequestListeners = new ArrayList<>();
    private final List<ServletRequestAttributeListener> _servletRequestAttributeListeners = new ArrayList<>();

    private static final String WEB_INF = "WEB-INF/";
    private static final String WEB_INF_CLASSES = WEB_INF + "classes/";
    private static final String WEB_INF_LIB = WEB_INF + "lib/";

    private class PrefixPattern implements Comparable
    {
        public final String pattern;
        public final ServletReg servlet;

        public PrefixPattern(String p, ServletReg s)
        {
            pattern = p;
            servlet = s;
        }

        public boolean match(String url)
        {
            return url.startsWith(pattern) && (
                url.length() == pattern.length()
                || url.charAt(pattern.length()) == '/');
        }

        @Override
        public int compareTo(Object p)
        {
            return pattern.length() - ((PrefixPattern) p).pattern.length();
        }
    }

    public static Context start(String webapp, URL[] classpaths) throws Exception
    {
        Context ctx = new Context();

        ctx.loadApp(webapp, classpaths);
        ctx.initialized();

        return ctx;
    }

    public Context()
    {
    }

    public void loadApp(String webapp, URL[] classpaths) throws Exception
    {
        File root = new File(webapp);
        if (!root.exists()) {
            throw new FileNotFoundException(
                "Unable to determine code source archive from " + root);
        }


        URLClassLoader ucl = (URLClassLoader) Thread.currentThread().getContextClassLoader();

        for (URL u : ucl.getURLs()) {
            trace("URLs: " + u);
        }


        ArrayList<URL> url_list = new ArrayList<>();

        if (!root.isDirectory()) {
            root = extractWar(root);
            extracted_dir_ = root;
        }

        File web_inf_classes = new File(root, WEB_INF_CLASSES);
        if (web_inf_classes.exists() && web_inf_classes.isDirectory()) {
            url_list.add(new URL("file:" + root.getAbsolutePath() + "/" + WEB_INF_CLASSES));
        }

        File lib = new File(root, WEB_INF_LIB);
        File[] libs = lib.listFiles();

        if (libs != null) {
            for (File l : libs) {
                url_list.add(new URL("file:" + l.getAbsolutePath()));
            }
        }

        for (URL u : classpaths) {
            url_list.add(u);
        }

        URL[] urls = new URL[url_list.size()];

        for (int i = 0; i < url_list.size(); i++) {
            urls[i] = url_list.get(i);
            trace("archives: " + urls[i]);
        }

        loader_ = new CtxClassLoader(urls,
            Context.class.getClassLoader().getParent());

        ClassLoader old = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(loader_);

        try {
            processWebXml(root);

            if (metadata_complete_) {
                return;
            }

            ScanResult scan_res = new ClassGraph()
                //.verbose()
                .overrideClassLoaders(loader_)
                //.ignoreParentClassLoaders()
                .enableClassInfo()
                .enableAnnotationInfo()
                .enableSystemPackages()
                //.enableAllInfo()
                .scan();

            loadInitializers(scan_res);

            if (metadata_complete_) {
                return;
            }

            scanClasses(scan_res);

        } finally {
            Thread.currentThread().setContextClassLoader(old);

            Collections.sort(prefix_patterns_, Collections.reverseOrder());
        }
    }

    private class CtxClassLoader extends URLClassLoader
    {
        public CtxClassLoader(URL[] urls, ClassLoader parent)
        {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException
        {
            // trace("Loader.loadClass: " + name + "; " + resolve);

            // Has this loader loaded the class already?
            Class<?> webapp_class = findLoadedClass(name);
            if (webapp_class != null) {
                //trace("found " + name + " loaded");
                return webapp_class;
            }

            if (name.startsWith("java.") || name.startsWith("javax.")) {
                return super.loadClass(name, resolve);
            }

            // Try the webapp classloader first
            // Look in the webapp classloader as a resource, to avoid 
            // loading a system class.
            String path = name.replace('.', '/').concat(".class");
            URL webapp_url = findResource(path);

            if (webapp_url != null) {
                webapp_class = super.findClass(name); //,webapp_url);
                resolveClass(webapp_class);
                //trace("webapp loaded " + name);
                return webapp_class;
            }

            return super.loadClass(name, resolve);
        }
    }

    private File extractWar(File war) throws IOException
    {
        Path tmpDir = Files.createTempDirectory("webapp");

        JarFile jf = new JarFile(war);

        for (Enumeration<JarEntry> en = jf.entries(); en.hasMoreElements();) {
            JarEntry e = en.nextElement();
            long mod_time = e.getTime();
            Path ep = tmpDir.resolve(e.getName());
            Path p;
            if (e.isDirectory()) {
                p = ep;
            } else {
                p = ep.getParent();
            }
            if (!p.toFile().isDirectory()) {
                Files.createDirectories(ep.getParent());
            }

            if (!e.isDirectory()) {
                Files.copy(jf.getInputStream(e), ep,
                    StandardCopyOption.REPLACE_EXISTING);
            }

            if (mod_time > 0) {
                ep.toFile().setLastModified(mod_time);
            }
        }

        return tmpDir.toFile();
    }

    private class CtxFilterChain implements FilterChain
    {
        private int filter_index_ = 0;
        private final ServletReg servlet_;

        CtxFilterChain(ServletReg servlet)
        {
            servlet_ = servlet;
        }

        @Override
        public void doFilter (ServletRequest request, ServletResponse response)
            throws IOException, ServletException
        {
            if (filter_index_ < filters_.size()) {
                filters_.get(filter_index_++).filter_.doFilter(request, response, this);

                return;
            }

            servlet_.service(request, response);
        }
    }

    private ServletReg findServlet(String path)
    {
        return findServlet(path, null);
    }

    private ServletReg findServlet(String path, DynamicPathRequest req)
    {
        if (!path.startsWith(context_path_)) {
            trace("findServlet: '" + path + "' not started with '" + context_path_ + "'");
            return null;
        }
        path = path.substring(context_path_.length());

        ServletReg servlet = exact2servlet_.get(path);
        if (servlet != null) {
            trace("findServlet: '" + path + "' matched exact pattern");
            if (req != null) {
                req.setServletPath(path);
                req.setPathInfo(null);
            }
            return servlet;
        }

        for (PrefixPattern p : prefix_patterns_) {
            if (p.match(path)) {
                trace("findServlet: '" + path + "' matched prefix pattern '" + p.pattern + "'");
                if (req != null) {
                    req.setServletPath(p.pattern);
                    req.setPathInfo(path.substring(p.pattern.length(), path.length()));
                }
                return p.servlet;
            }
        }

        if (default_servlet_ != null) {
            trace("findServlet: '" + path + "' matched default servlet");
            if (req != null) {
                req.setServletPath(path);
                req.setPathInfo(null);
            }
            return default_servlet_;
        }

        trace("findServlet: '" + path + "' no servlet found");
        return null;
    }

    public void service(Request req, ServletResponse resp)
        throws ServletException, IOException
    {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(loader_);

        try {
            URI uri = new URI(req.getRequestURI());
            FilterChain fc = new CtxFilterChain(findServlet(uri.getPath(), req));

            fc.doFilter(req, resp);
        } catch (URISyntaxException e) {
            throw new ServletException(e);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    private void processWebXml(File root) throws Exception
    {
        if (root.isDirectory()) {
            File web_xml = new File(root, "WEB-INF/web.xml");
            if (web_xml.exists()) {
                trace("start: web.xml file found");

                InputStream is = new FileInputStream(web_xml);

                processWebXml(is);

                is.close();
            }
        } else {
            JarFile jf = new JarFile(root);
            ZipEntry ze = jf.getEntry("WEB-INF/web.xml");

            if (ze == null) {
                trace("start: web.xml entry NOT found");
            } else {
                trace("start: web.xml entry found");

                processWebXml(jf.getInputStream(ze));
            }

            jf.close();
        }
    }

    private void processWebXml(InputStream is)
        throws ParserConfigurationException, SAXException, IOException
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        Document doc = builder.parse(is);

        Element doc_elem = doc.getDocumentElement();
        String doc_elem_name = doc_elem.getNodeName();
        if (!doc_elem_name.equals("web-app")) {
            throw new RuntimeException("Invalid web.xml: 'web-app' element expected, not '" + doc_elem_name + "'");
        }

        metadata_complete_ = doc_elem.getAttribute("metadata-complete").equals("true");
        app_version_ = doc_elem.getAttribute("version");
        if (compareVersion(app_version_, "3.0") < 0) {
            metadata_complete_ = true;
        }

        NodeList context_params = doc_elem.getElementsByTagName("context-param");
        for(int i = 0; i < context_params.getLength(); i++) {
            processXmlInitParam(this, (Element) context_params.item(i));
        }

        NodeList filters = doc_elem.getElementsByTagName("filter");

        for(int i = 0; i < filters.getLength(); i++) {
            Element filter_el = (Element) filters.item(i);
            NodeList names = filter_el.getElementsByTagName("filter-name");
            if (names == null || names.getLength() != 1) {
                throw new RuntimeException("Invalid web.xml: 'filter-name' tag not found");
            }

            String filter_name = names.item(0).getTextContent();
            trace("filter-name=" + filter_name);

            FilterReg reg = new FilterReg(filter_name);

            NodeList child_nodes = filter_el.getChildNodes();
            for(int j = 0; j < child_nodes.getLength(); j++) {
                Node child_node = child_nodes.item(j);
                String tag_name = child_node.getNodeName();

                if (tag_name.equals("filter-class")) {
                    reg.setClassName(child_node.getTextContent());
                    continue;
                }

                if (tag_name.equals("async-supported")) {
                    reg.setAsyncSupported(child_node.getTextContent().equals("true"));
                    continue;
                }

                if (tag_name.equals("init-param")) {
                    processXmlInitParam(reg, (Element) child_node);
                    continue;
                }
            }

            filters_.add(reg);
            name2filter_.put(filter_name, reg);
        }

        NodeList filter_mappings = doc_elem.getElementsByTagName("filter-mapping");

        for(int i = 0; i < filter_mappings.getLength(); i++) {
            Element mapping_el = (Element) filter_mappings.item(i);
            NodeList names = mapping_el.getElementsByTagName("filter-name");
            if (names == null || names.getLength() != 1) {
                throw new RuntimeException("Invalid web.xml: 'filter-name' tag not found");
            }

            String filter_name = names.item(0).getTextContent();
            trace("filter-name=" + filter_name);

            FilterReg reg = name2filter_.get(filter_name);
            if (reg == null) {
                throw new RuntimeException("Invalid web.xml: filter '" + filter_name + "' not found");
            }

            EnumSet<DispatcherType> dtypes = EnumSet.noneOf(DispatcherType.class);
            NodeList dispatchers = mapping_el.getElementsByTagName("dispatcher");
            for (int j = 0; j < dispatchers.getLength(); j++) {
                Node child_node = dispatchers.item(j);
                dtypes.add(DispatcherType.valueOf(child_node.getTextContent()));
            }

            if (dtypes.isEmpty()) {
                dtypes.add(DispatcherType.REQUEST);
            }

            boolean match_after = false;

            NodeList child_nodes = mapping_el.getChildNodes();
            for (int j = 0; j < child_nodes.getLength(); j++) {
                Node child_node = child_nodes.item(j);
                String tag_name = child_node.getNodeName();

                if (tag_name.equals("url-pattern")) {
                    reg.addMappingForUrlPatterns(dtypes, match_after, child_node.getTextContent());
                    continue;
                }

                if (tag_name.equals("servlet-name")) {
                    reg.addMappingForServletNames(dtypes, match_after, child_node.getTextContent());
                    continue;
                }
            }
        }

        NodeList servlets = doc_elem.getElementsByTagName("servlet");

        for (int i = 0; i < servlets.getLength(); i++) {
            Element servlet_el = (Element) servlets.item(i);
            NodeList names = servlet_el.getElementsByTagName("servlet-name");
            if (names == null || names.getLength() != 1) {
                throw new RuntimeException("Invalid web.xml: 'servlet-name' tag not found");
            }

            String servlet_name = names.item(0).getTextContent();
            trace("servlet-name=" + servlet_name);

            ServletReg reg = new ServletReg(servlet_name);

            NodeList child_nodes = servlet_el.getChildNodes();
            for(int j = 0; j < child_nodes.getLength(); j++) {
                Node child_node = child_nodes.item(j);
                String tag_name = child_node.getNodeName();

                if (tag_name.equals("servlet-class")) {
                    reg.setClassName(child_node.getTextContent());
                    continue;
                }

                if (tag_name.equals("async-supported")) {
                    reg.setAsyncSupported(child_node.getTextContent().equals("true"));
                    continue;
                }

                if (tag_name.equals("init-param")) {
                    processXmlInitParam(reg, (Element) child_node);
                    continue;
                }

                if (tag_name.equals("load-on-startup")) {
                    reg.setLoadOnStartup(Integer.parseInt(child_node.getTextContent()));
                    continue;
                }
            }

            servlets_.add(reg);
            name2servlet_.put(servlet_name, reg);
        }

        NodeList servlet_mappings = doc_elem.getElementsByTagName("servlet-mapping");

        for(int i = 0; i < servlet_mappings.getLength(); i++) {
            Element mapping_el = (Element) servlet_mappings.item(i);
            NodeList names = mapping_el.getElementsByTagName("servlet-name");
            if (names == null || names.getLength() != 1) {
                throw new RuntimeException("Invalid web.xml: 'servlet-name' tag not found");
            }

            String servlet_name = names.item(0).getTextContent();
            trace("servlet-name=" + servlet_name);

            ServletReg reg = name2servlet_.get(servlet_name);
            if (reg == null) {
                throw new RuntimeException("Invalid web.xml: servlet '" + servlet_name + "' not found");
            }

            NodeList child_nodes = mapping_el.getElementsByTagName("url-pattern");
            String patterns[] = new String[child_nodes.getLength()];
            for(int j = 0; j < child_nodes.getLength(); j++) {
                Node child_node = child_nodes.item(j);
                patterns[j] = child_node.getTextContent();
            }

            reg.addMapping(patterns);
        }

        NodeList listeners = doc_elem.getElementsByTagName("listener");

        for (int i = 0; i < listeners.getLength(); i++) {
            Element listener_el = (Element) listeners.item(i);
            NodeList classes = listener_el.getElementsByTagName("listener-class");
            if (classes == null || classes.getLength() != 1) {
                throw new RuntimeException("Invalid web.xml: 'listener-class' tag not found");
            }

            String class_name = classes.item(0).getTextContent();
            trace("listener-class=" + class_name);

            addListener(class_name);
        }
    }

    private static int compareVersion(String ver1, String ver2)
    {
        String[] varr1 = ver1.split("\\.");
        String[] varr2 = ver2.split("\\.");

        int max_len = varr1.length > varr2.length ? varr1.length : varr2.length;
        for (int i = 0; i < max_len; i++) {
            int l = i < varr1.length ? Integer.parseInt(varr1[i]) : 0;
            int r = i < varr2.length ? Integer.parseInt(varr2[i]) : 0;

            int res = l - r;

            if (res != 0) {
                return res;
            }
        }

        return 0;
    }

    private void processXmlInitParam(InitParams params, Element elem)
          throws RuntimeException
    {
        NodeList n = elem.getElementsByTagName("param-name");
        if (n == null || n.getLength() != 1) {
            throw new RuntimeException("Invalid web.xml: 'param-name' tag not found");
        }

        NodeList v = elem.getElementsByTagName("param-value");
        if (v == null || v.getLength() != 1) {
            throw new RuntimeException("Invalid web.xml: 'param-value' tag not found");
        }
        params.setInitParameter(n.item(0).getTextContent(), v.item(0).getTextContent());
    }

    private void loadInitializers(ScanResult scan_res)
    {
        trace("load initializer(s)");

        ServiceLoader<ServletContainerInitializer> initializers =
            ServiceLoader.load(ServletContainerInitializer.class, loader_);

        for (ServletContainerInitializer sci : initializers) {

            trace("loadInitializers: initializer: " + sci.getClass().getName());

            HandlesTypes ann = sci.getClass().getAnnotation(HandlesTypes.class);
            if (ann == null) {
                trace("loadInitializers: no HandlesTypes annotation");
                continue;
            }

            Class<?>[] classes = ann.value();
            if (classes == null) {
                trace("loadInitializers: no handles classes");
                continue;
            }

            Set<Class<?>> handles_classes = new HashSet<>();

            for (Class<?> c : classes) {
                trace("loadInitializers: find handles: " + c.getName());

                ClassInfoList handles = c.isInterface()
                    ? scan_res.getClassesImplementing(c.getName())
                    : scan_res.getSubclasses(c.getName());

                for (ClassInfo ci : handles) {
                    if (ci.isInterface()
                        || ci.isAnnotation()
                        || ci.isAbstract()
                        || ci.isInnerClass())
                    {
                        continue;
                    }

                    trace("loadInitializers: handles class: " + ci.getName());
                    handles_classes.add(ci.loadClass());
                }
            }

            if (handles_classes.isEmpty()) {
                trace("loadInitializers: no handles implementations");
                continue;
            }

            try {
                sci.onStartup(handles_classes, this);
                metadata_complete_ = true;
            } catch(Exception e) {
                System.err.println("loadInitializers: exception caught: " + e.toString());
            }
        }
    }

    private void scanClasses(ScanResult scan_res)
    {
        ClassInfoList filters = scan_res.getClassesImplementing(Filter.class.getName());

        for (ClassInfo ci : filters) {
            if (ci.isInterface()
                || ci.isAnnotation()
                || ci.isAbstract()
                || ci.isInnerClass())
            {
                trace("scanClasses: ignoring Filter impl: " + ci.getName());
                continue;
            }

            trace("scanClasses: found Filter class: " + ci.getName());

            String filter_name = ci.getName();
            Class<?> cls = ci.loadClass();
            WebFilter ann = cls.getAnnotation(WebFilter.class);

            if (ann != null) {
                filter_name = ann.filterName();
            }

            FilterReg reg = name2filter_.get(filter_name);

            if (reg == null) {
                reg = new FilterReg(filter_name, cls);
                filters_.add(reg);
                name2filter_.put(filter_name, reg);
            } else {
                reg.setClass(cls);
            }

            if (ann == null) {
                trace("scanClasses: no WebFilter annotation");
                continue;
            }

            EnumSet<DispatcherType> dtypes = EnumSet.noneOf(DispatcherType.class);
            DispatcherType[] dispatchers = ann.dispatcherTypes();
            for (DispatcherType d : dispatchers) {
                dtypes.add(d);
            }

            if (dtypes.isEmpty()) {
                dtypes.add(DispatcherType.REQUEST);
            }

            boolean match_after = false;

            reg.addMappingForUrlPatterns(dtypes, match_after, ann.value());
            reg.addMappingForUrlPatterns(dtypes, match_after, ann.urlPatterns());
            reg.addMappingForServletNames(dtypes, match_after, ann.servletNames());

            for (WebInitParam p : ann.initParams()) {
                reg.setInitParameter(p.name(), p.value());
            }

            reg.setAsyncSupported(ann.asyncSupported());
        }

        ClassInfoList servlets = scan_res.getSubclasses(HttpServlet.class.getName());

        for (ClassInfo ci : servlets) {
            if (ci.isInterface()
                || ci.isAnnotation()
                || ci.isAbstract()
                || ci.isInnerClass())
            {
                trace("scanClasses: ignoring HttpServlet subclass: " + ci.getName());
                continue;
            }

            trace("scanClasses: found HttpServlet class: " + ci.getName());

            String servlet_name = ci.getName();
            Class<?> cls = ci.loadClass();
            WebServlet ann = cls.getAnnotation(WebServlet.class);

            if (ann != null) {
                servlet_name = ann.name();
            }

            ServletReg reg = name2servlet_.get(servlet_name);

            if (reg == null) {
                reg = new ServletReg(servlet_name, cls);
                servlets_.add(reg);
                name2servlet_.put(servlet_name, reg);
            } else {
                reg.setClass(cls);
            }

            if (ann == null) {
                trace("scanClasses: no WebServlet annotation");
                continue;
            }

            reg.addMapping(ann.value());
            reg.addMapping(ann.urlPatterns());

            for (WebInitParam p : ann.initParams()) {
                reg.setInitParameter(p.name(), p.value());
            }

            reg.setAsyncSupported(ann.asyncSupported());
        }

        for (Class<?> lstnr_type : SERVLET_LISTENER_TYPES) {
            ClassInfoList lstnrs = scan_res.getClassesImplementing(lstnr_type.getName());

            for (ClassInfo ci : lstnrs) {
                if (ci.isInterface()
                    || ci.isAnnotation()
                    || ci.isAbstract()
                    || ci.isInnerClass())
                {
                    trace("scanClasses: listener impl: " + ci.getName());
                    continue;
                }

                trace("scanClasses: listener class: " + ci.getName());
            }
        }
    }

    public void stop() throws IOException
    {
        if (extracted_dir_ != null) {
            removeDir(extracted_dir_);
        }
    }

    private void removeDir(File dir) throws IOException
    {
        Files.walkFileTree(dir.toPath(),
            new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult postVisitDirectory(
                  Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(
                  Path file, BasicFileAttributes attrs) 
                  throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
            });
    }

    private class CtxInitParams implements InitParams
    {
        private final Map<String, String> init_params_ =
            new HashMap<String, String>();

        public boolean setInitParameter(String name, String value)
        {
            trace("CtxInitParams.setInitParameter " + name + " = " + value);

            return init_params_.putIfAbsent(name, value) == null;
        }

        public String getInitParameter(String name)
        {
            trace("CtxInitParams.getInitParameter for " + name);

            return init_params_.get(name);
        }

        public Set<String> setInitParameters(Map<String, String> initParameters)
        {
            // illegalStateIfContextStarted();
            Set<String> clash = null;
            for (Map.Entry<String, String> entry : initParameters.entrySet())
            {
                if (entry.getKey() == null) {
                    throw new IllegalArgumentException("init parameter name required");
                }

                if (entry.getValue() == null) {
                    throw new IllegalArgumentException("non-null value required for init parameter " + entry.getKey());
                }

                if (init_params_.get(entry.getKey()) != null)
                {
                    if (clash == null)
                        clash = new HashSet<String>();
                    clash.add(entry.getKey());
                }

                trace("CtxInitParams.setInitParameters " + entry.getKey() + " = " + entry.getValue());
            }

            if (clash != null) {
                return clash;
            }

            init_params_.putAll(initParameters);
            return Collections.emptySet();
        }

        public Map<String, String> getInitParameters()
        {
            trace("CtxInitParams.getInitParameters");
            return init_params_;
        }

        public Enumeration<String> getInitParameterNames()
        {
            return Collections.enumeration(init_params_.keySet());
        }
    }

    private class NamedReg extends CtxInitParams
        implements Registration
    {
        private final String name_;
        private String class_name_;

        public NamedReg(String name)
        {
            name_ = name;
        }

        public NamedReg(String name, String class_name)
        {
            name_ = name;
            class_name_ = class_name;
        }

        @Override
        public String getName()
        {
            return name_;
        }

        @Override
        public String getClassName()
        {
            return class_name_;
        }

        public void setClassName(String class_name)
        {
            class_name_ = class_name;
        }
    }

    private class ServletReg extends NamedReg
        implements ServletRegistration.Dynamic, ServletConfig
    {
        private Class<?> servlet_class_;
        private Servlet servlet_;
        private String role_;
        private boolean async_supported_ = false;
        private final List<String> patterns_ = new ArrayList<String>();
        private int load_on_startup_ = -1;
        private boolean initialized_ = false;

        public ServletReg(String name, Class<?> servlet_class)
        {
            super(name, servlet_class.getName());
            servlet_class_ = servlet_class;
        }

        public ServletReg(String name, Servlet servlet)
        {
            super(name, servlet.getClass().getName());
            servlet_ = servlet;
        }

        public ServletReg(String name, String servlet_class_name)
        {
            super(name, servlet_class_name);
        }

        public ServletReg(String name)
        {
            super(name);
        }

        private void init() throws ServletException
        {
            if (initialized_) {
                return;
            }

            if (servlet_ == null) {
                try {
                    if (servlet_class_ == null) {
                        servlet_class_ = loader_.loadClass(getClassName());
                    }

                    Constructor<?> ctor = servlet_class_.getConstructor();
                    servlet_ = (Servlet) ctor.newInstance();
                } catch(Exception e) {
                    log("ServletReg.init() failed " + e);
                    throw new ServletException(e);
                }
            }

            servlet_.init((ServletConfig) this);

            initialized_ = true;
        }

        public void startup() throws ServletException
        {
            if (load_on_startup_ < 0) {
                return;
            }

            init();
        }

        public void setClassName(String class_name) throws IllegalStateException
        {
            if (servlet_ != null
                || servlet_class_ != null
                || getClassName() != null)
            {
                throw new IllegalStateException("Class already initialized");
            }

            super.setClassName(class_name);
        }

        public void setClass(Class<?> servlet_class)
            throws IllegalStateException
        {
            if (servlet_ != null
                || servlet_class_ != null
                || getClassName() != null)
            {
                throw new IllegalStateException("Class already initialized");
            }

            super.setClassName(servlet_class.getName());
            servlet_class_ = servlet_class;
        }

        public void service(ServletRequest request, ServletResponse response)
            throws ServletException, IOException
        {
            init();

            servlet_.service(request, response);
        }

        @Override
        public Set<String> addMapping(String... urlPatterns)
        {
            // illegalStateIfContextStarted();
            Set<String> clash = null;
            for (String pattern : urlPatterns) {
                trace("ServletReg.addMapping: " + pattern);

                if (pattern2servlet_.containsKey(pattern)) {
                    if (clash == null) {
                        clash = new HashSet<String>();
                    }
                    clash.add(pattern);
                }
            }

            /* if there were any clashes amongst the urls, return them */
            if (clash != null) {
                return clash;
            }

            for (String pattern : urlPatterns) {
                patterns_.add(pattern);
                pattern2servlet_.put(pattern, this);
                parseURLPattern(pattern, this);
            }

            return Collections.emptySet();
        }

        public void parseURLPattern(String p, ServletReg servlet)
            throws IllegalArgumentException
        {
            if (p == "/") {
                trace("parseURLPattern: '" + p + "' is a default");
                default_servlet_ = servlet;
                return;
            }

            if (p == "") {
                trace("parseURLPattern: '" + p + "' is a root");
                exact2servlet_.put("/", servlet);
                return;
            }

            if (p.endsWith("/*") && p.startsWith("/")) {
                trace("parseURLPattern: '" + p + "' is a prefix pattern");
                p = p.substring(0, p.length() - 2);
                prefix_patterns_.add(new PrefixPattern(p, servlet));
                return;
            }

            if (p.startsWith("*.")) {
                trace("parseURLPattern: '" + p + "' is a suffix pattern");
                p = p.substring(1, p.length());
                suffix2servlet_.put(p, servlet);
                return;
            }

            trace("parseURLPattern: '" + p + "' is an exact pattern");
            exact2servlet_.put(p, servlet);

            /* TODO process other cases, throw IllegalArgumentException */
        }

        @Override
        public Collection<String> getMappings()
        {
            trace("ServletReg.getMappings");
            return patterns_;
        }

        @Override
        public String getRunAsRole()
        {
            return role_;
        }

        @Override
        public void setLoadOnStartup(int loadOnStartup)
        {
            log("ServletReg.setLoadOnStartup: " + loadOnStartup);
            load_on_startup_ = loadOnStartup;
        }

        @Override
        public Set<String> setServletSecurity(ServletSecurityElement constraint)
        {
            log("ServletReg.setServletSecurity");
            return Collections.emptySet();
        }

        @Override
        public void setMultipartConfig(
            MultipartConfigElement multipartConfig)
        {
            log("ServletReg.setMultipartConfig");
        }

        @Override
        public void setRunAsRole(String roleName)
        {
            log("ServletReg.setRunAsRole: " + roleName);
            role_ = roleName;
        }

        @Override
        public void setAsyncSupported(boolean isAsyncSupported)
        {
            log("ServletReg.setAsyncSupported: " + isAsyncSupported);
            async_supported_ = isAsyncSupported;
        }

        @Override
        public String getServletName()
        {
            return getName();
        }

        @Override
        public ServletContext getServletContext()
        {
            return (ServletContext) Context.this;
        }
    }

    private class FilterReg extends NamedReg
        implements FilterRegistration.Dynamic, FilterConfig
    {
        private Class<?> filter_class_;
        private Filter filter_;
        private boolean async_supported_ = false;

        public FilterReg(String name, Class<?> filter_class)
        {
            super(name, filter_class.getName());
            filter_class_ = filter_class;
        }

        public FilterReg(String name, Filter filter)
        {
            super(name, filter.getClass().getName());
            filter_ = filter;
        }

        public FilterReg(String name, String filter_class_name)
        {
            super(name, filter_class_name);
        }

        public FilterReg(String name)
        {
            super(name);
        }

        public void setClassName(String class_name) throws IllegalStateException
        {
            if (filter_ != null
                || filter_class_ != null
                || getClassName() != null)
            {
                throw new IllegalStateException("Class already initialized");
            }

            super.setClassName(class_name);
        }

        public void setClass(Class<?> filter_class) throws IllegalStateException
        {
            if (filter_ != null
                || filter_class_ != null
                || getClassName() != null)
            {
                throw new IllegalStateException("Class already initialized");
            }

            super.setClassName(filter_class.getName());
            filter_class_ = filter_class;
        }

        @Override
        public void addMappingForServletNames(
            EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter,
            String... servletNames)
        {
            for (String n : servletNames) {
                log("FilterReg.addMappingForServletNames: ... " + n);
            }
        }

        @Override
        public Collection<String> getServletNameMappings()
        {
            log("FilterReg.getServletNameMappings");
            return Collections.emptySet();
        }

        @Override
        public void addMappingForUrlPatterns(
            EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter,
            String... urlPatterns)
        {
            for (String u : urlPatterns) {
                log("FilterReg.addMappingForUrlPatterns: ... " + u);
            }
        }

        @Override
        public Collection<String> getUrlPatternMappings()
        {
            log("FilterReg.getUrlPatternMappings");
            return Collections.emptySet();
        }

        @Override
        public void setAsyncSupported(boolean isAsyncSupported)
        {
            log("FilterReg.setAsyncSupported: " + isAsyncSupported);
            async_supported_ = isAsyncSupported;
        }

        @Override
        public String getFilterName()
        {
            return getName();
        }

        @Override
        public ServletContext getServletContext()
        {
            return (ServletContext) Context.this;
        }
    }

    private void initialized()
    {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(loader_);

        try {
            // Call context listeners
            _destroyServletContextListeners.clear();
            if (!_servletContextListeners.isEmpty()) {
                ServletContextEvent event = new ServletContextEvent(this);
                for (ServletContextListener listener : _servletContextListeners)
                {
                    trace("call contextInitialized");
                    listener.contextInitialized(event);
                    trace("call contextInitialized done");
                    _destroyServletContextListeners.add(listener);
                }
            }

            for (ServletReg sr : servlets_) {
                try {
                    sr.startup();
                } catch(ServletException e) {
                    System.err.println("initialized: exception caught: " + e.toString());
                }
            }

            for (FilterReg fr : filters_) {
                try {
                    fr.filter_.init((FilterConfig) fr);
                } catch(ServletException e) {
                    System.err.println("initialized: exception caught: " + e.toString());
                }
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Override
    public ServletContext getContext(String uripath)
    {
        trace("getContext for " + uripath);
        return this;
    }

    @Override
    public int getMajorVersion()
    {
        trace("getMajorVersion");
        return SERVLET_MAJOR_VERSION;
    }

    @Override
    public String getMimeType(String file)
    {
        log("getMimeType for " + file);
        if (mime_types_ == null) {
            mime_types_ = new MimeTypes();
        }
        return mime_types_.getMimeByExtension(file);
    }

    @Override
    public int getMinorVersion()
    {
        trace("getMinorVersion");
        return SERVLET_MINOR_VERSION;
    }

    private class CtxRequestDispatcher implements RequestDispatcher
    {
        private String uri_;

        public CtxRequestDispatcher(String uri)
        {
            uri_ = uri;
        }

        @Override
        public void forward(ServletRequest request, ServletResponse response)
            throws ServletException, IOException
        {
            try {
                log("CtxRequestDispatcher.forward");
                Request r = (Request) request;

                String servlet_path = r.getServletPath();
                String path_info = r.getPathInfo();
                String req_uri = r.getRequestURI();
                DispatcherType dtype = r.getDispatcherType();

                URI uri = new URI(req_uri);
                uri = uri.resolve(uri_);

                r.setRequestURI(uri.getRawPath());
                r.setDispatcherType(DispatcherType.FORWARD);

                ServletReg servlet = findServlet(uri.getPath(), r);
                servlet.service(r, response);

                r.setServletPath(servlet_path);
                r.setPathInfo(path_info);
                r.setRequestURI(req_uri);
                r.setDispatcherType(dtype);

                log("CtxRequestDispatcher.forward done");
            } catch (URISyntaxException e) {
                throw new ServletException(e);
            }
        }

        @Override
        public void include(ServletRequest request, ServletResponse response)
            throws ServletException, IOException
        {
            try {
                log("CtxRequestDispatcher.include");
                Request r = (Request) request;

                DispatcherType dtype = request.getDispatcherType();

                r.setDispatcherType(DispatcherType.INCLUDE);

                URI uri = new URI(r.getRequestURI());
                uri = uri.resolve(uri_);

                ServletReg servlet = findServlet(uri.getPath());
                servlet.service(r, response);

                r.setDispatcherType(dtype);

                log("CtxRequestDispatcher.include done");
            } catch (URISyntaxException e) {
                throw new ServletException(e);
            }
        }
    }

    @Override
    public RequestDispatcher getNamedDispatcher(String name)
    {
        log("getNamedDispatcher for " + name);
        return null;
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String uriInContext)
    {
        log("getRequestDispatcher for " + uriInContext);
        return new CtxRequestDispatcher(uriInContext);
    }

    @Override
    public String getRealPath(String path)
    {
        log("getRealPath for " + path);
        return null;
    }

    @Override
    public URL getResource(String path) throws MalformedURLException
    {
        log("getResource for " + path);
        return null;
    }

    @Override
    public InputStream getResourceAsStream(String path)
    {
        log("getResourceAsStream for " + path);
        return null;
    }

    @Override
    public Set<String> getResourcePaths(String path)
    {
        log("getResourcePaths for " + path);
        return null;
    }

    @Override
    public String getServerInfo()
    {
        log("getServerInfo for " + server_info_);
        return server_info_;
    }

    @Override
    @Deprecated
    public Servlet getServlet(String name) throws ServletException
    {
        log("getServlet for " + name);
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    @Deprecated
    public Enumeration<String> getServletNames()
    {
        log("getServletNames");
        return Collections.enumeration(Collections.EMPTY_LIST);
    }

    @SuppressWarnings("unchecked")
    @Override
    @Deprecated
    public Enumeration<Servlet> getServlets()
    {
        log("getServlets");
        return Collections.enumeration(Collections.EMPTY_LIST);
    }

    @Override
    @Deprecated
    public void log(Exception exception, String msg)
    {
        log(msg, exception);
    }

    @Override
    public void log(String msg)
    {
        msg = "Context." + msg;
        log(0, msg, msg.length());
    }

    @Override
    public void log(String message, Throwable throwable)
    {
        log(message);
    }

    private static native void log(long ctx_ptr, String msg, int msg_len);


    public static void trace(String msg)
    {
        msg = "Context." + msg;
        trace(0, msg, msg.length());
    }

    private static native void trace(long ctx_ptr, String msg, int msg_len);

    @Override
    public String getInitParameter(String name)
    {
        trace("getInitParameter for " + name);
        return init_params_.get(name);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Enumeration<String> getInitParameterNames()
    {
        trace("getInitParameterNames");
        return Collections.enumeration(Collections.EMPTY_LIST);
    }

    @Override
    public String getServletContextName()
    {
        log("getServletContextName");
        return "No Context";
    }

    @Override
    public String getContextPath()
    {
        log("getContextPath");
        return context_path_;
    }

    @Override
    public boolean setInitParameter(String name, String value)
    {
        trace("setInitParameter " + name + " = " + value);
        return init_params_.putIfAbsent(name, value) == null;
    }

    @Override
    public Object getAttribute(String name)
    {
        trace("getAttribute " + name);
        return super.getAttribute(name);
    }

    @Override
    public void setAttribute(String name, Object object)
    {
        trace("setAttribute " + name);
        super.setAttribute(name, object);
    }

    @Override
    public void removeAttribute(String name)
    {
        trace("removeAttribute " + name);
        super.removeAttribute(name);
    }

    @Override
    public FilterRegistration.Dynamic addFilter(String name,
        Class<? extends Filter> filterClass)
    {
        log("addFilter<C> " + name + ", " + filterClass.getName());

        FilterReg reg = new FilterReg(name, filterClass);
        filters_.add(reg);
        name2filter_.put(name, reg);
        return reg;
    }

    @Override
    public FilterRegistration.Dynamic addFilter(String name, Filter filter)
    {
        log("addFilter<F> " + name);

        FilterReg reg = new FilterReg(name, filter);
        filters_.add(reg);
        name2filter_.put(name, reg);
        return reg;
    }

    @Override
    public FilterRegistration.Dynamic addFilter(String name, String className)
    {
        log("addFilter<N> " + name + ", " + className);

        FilterReg reg = new FilterReg(name, className);
        filters_.add(reg);
        name2filter_.put(name, reg);
        return reg;
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String name,
        Class<? extends Servlet> servletClass)
    {
        log("addServlet<C> " + name + ", " + servletClass.getName());

        ServletReg reg = null;
        try {
            reg = new ServletReg(name, servletClass);
            servlets_.add(reg);
            name2servlet_.put(name, reg);
        } catch(Exception e) {
            System.err.println("addServlet: exception caught: " + e.toString());
        }

        return reg;
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String name, Servlet servlet)
    {
        log("addServlet<S> " + name);

        ServletReg reg = null;
        try {
            reg = new ServletReg(name, servlet);
            servlets_.add(reg);
            name2servlet_.put(name, reg);
        } catch(Exception e) {
            System.err.println("addServlet: exception caught: " + e.toString());
        }

        return reg;
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String name, String className)
    {
        log("addServlet<N> " + name + ", " + className);

        ServletReg reg = null;
        try {
            reg = new ServletReg(name, className);
            servlets_.add(reg);
            name2servlet_.put(name, reg);
        } catch(Exception e) {
            System.err.println("addServlet: exception caught: " + e.toString());
        }

        return reg;
    }

    @Override
    public <T extends Filter> T createFilter(Class<T> c) throws ServletException
    {
        log("createFilter<C> " + c.getName());

        try {
            Constructor<T> ctor = c.getConstructor();
            T filter = ctor.newInstance();
            return filter;
        } catch (Exception e) {
            log("createFilter() failed " + e);

            throw new ServletException(e);
        }
    }

    @Override
    public <T extends Servlet> T createServlet(Class<T> c) throws ServletException
    {
        log("createServlet<C> " + c.getName());

        try {
            Constructor<T> ctor = c.getConstructor();
            T servlet = ctor.newInstance();
            return servlet;
        } catch (Exception e) {
            log("createServlet() failed " + e);

            throw new ServletException(e);
        }
    }

    @Override
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes()
    {
        log("getDefaultSessionTrackingModes");
        return null;
    }

    @Override
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes()
    {
        log("getEffectiveSessionTrackingModes");
        return null;
    }

    @Override
    public FilterRegistration getFilterRegistration(String filterName)
    {
        log("getFilterRegistration " + filterName);
        return name2filter_.get(filterName);
    }

    @Override
    public Map<String, ? extends FilterRegistration> getFilterRegistrations()
    {
        log("getFilterRegistrations");
        return name2filter_;
    }

    @Override
    public ServletRegistration getServletRegistration(String servletName)
    {
        log("getServletRegistration " + servletName);
        return name2servlet_.get(servletName);
    }

    @Override
    public Map<String, ? extends ServletRegistration> getServletRegistrations()
    {
        log("getServletRegistrations");
        return name2servlet_;
    }

    @Override
    public SessionCookieConfig getSessionCookieConfig()
    {
        log("getSessionCookieConfig");
        //LOG.warn(__unimplmented);
        return null;
    }

    @Override
    public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes)
    {
        log("setSessionTrackingModes");
        //LOG.warn(__unimplmented);
    }

    @Override
    public void addListener(String className)
    {
        log("addListener<N> " + className);

        try {
            Class<?> cls = loader_.loadClass(className);

            Constructor<?> ctor = cls.getConstructor();
            EventListener listener = (EventListener) ctor.newInstance();

            addListener(listener);
        } catch (Exception e) {
            log("addListener<N>: exception caught: " + e.toString());
        }
    }

    @Override
    public <T extends EventListener> void addListener(T t)
    {
        log("addListener<T> " + t.getClass().getName());

        for (int i = 0; i < SERVLET_LISTENER_TYPES.length; i++) {
            Class<?> c = SERVLET_LISTENER_TYPES[i];
            if (c.isAssignableFrom(t.getClass())) {
                log("addListener<T>: assignable to " + c.getName());
            }
        }

        if (t instanceof ServletContextListener) {
            _servletContextListeners.add((ServletContextListener) t);
        }
    }

    @Override
    public void addListener(Class<? extends EventListener> listenerClass)
    {
        log("addListener<C> " + listenerClass.getName());

        try {
            Constructor<?> ctor = listenerClass.getConstructor();
            EventListener listener = (EventListener) ctor.newInstance();

            addListener(listener);
        } catch (Exception e) {
            log("addListener<C>: exception caught: " + e.toString());
        }
    }

    @Override
    public <T extends EventListener> T createListener(Class<T> clazz)
        throws ServletException
    {
        log("createListener<C> " + clazz.getName());

        try
        {
            return clazz.getDeclaredConstructor().newInstance();
        }
        catch (Exception e)
        {
            throw new ServletException(e);
        }
    }

    @Override
    public ClassLoader getClassLoader()
    {
        log("getClassLoader");
        return loader_;
    }

    @Override
    public int getEffectiveMajorVersion()
    {
        log("getEffectiveMajorVersion");
        return SERVLET_MAJOR_VERSION;
    }

    @Override
    public int getEffectiveMinorVersion()
    {
        log("getEffectiveMinorVersion");
        return SERVLET_MINOR_VERSION;
    }

    @Override
    public JspConfigDescriptor getJspConfigDescriptor()
    {
        log("getJspConfigDescriptor");
        //LOG.warn(__unimplmented);
        return null;
    }

    @Override
    public void declareRoles(String... roleNames)
    {
        log("declareRoles");
        //LOG.warn(__unimplmented);
    }

    @Override
    public String getVirtualServerName()
    {
        log("getVirtualServerName");
        return null;
    }
}
