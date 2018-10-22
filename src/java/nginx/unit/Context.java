package nginx.unit;

import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;

import java.lang.ClassLoader;
import java.lang.ClassNotFoundException;
import java.lang.IllegalArgumentException;
import java.lang.reflect.Constructor;

import java.net.MalformedURLException;
import java.net.URL;

import java.util.ArrayList;
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
import javax.servlet.descriptor.JspConfigDescriptor;

import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.util.AttributesMap;

import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.ExplodedArchive;
import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.boot.loader.LaunchedURLClassLoader;

public class Context extends AttributesMap implements ServletContext
{
    public final static int SERVLET_MAJOR_VERSION = 3;
    public final static int SERVLET_MINOR_VERSION = 1;

    private String context_path_ = "";
    private String server_info_ = "unit";
    private MimeTypes mime_types_;

    private final Map<String, String> init_params_ = new HashMap<String, String>();
    private ClassLoader class_loader_;

    private static Context ctx_;

    private static final String WEB_INF = "WEB-INF/";
    private static final String WEB_INF_CLASSES = WEB_INF + "classes/";
    private static final String WEB_INF_LIB = WEB_INF + "lib/";
    private static final String WEB_INF_LIB_PROVIDED = WEB_INF + "lib-provided/";

    private static boolean isNestedArchive(Archive.Entry entry) {
        if (entry.isDirectory()) {
            return entry.getName().equals(WEB_INF_CLASSES);
        } else {
            return entry.getName().startsWith(WEB_INF_LIB); /*
                || entry.getName().startsWith(WEB_INF_LIB_PROVIDED); */
        }
    }

    public static void start(String webapp, String servlet) throws IOException
    {
        File root = new File(webapp);
        if (!root.exists()) {
            throw new IllegalStateException(
                "Unable to determine code source archive from " + root);
        }

        Archive archive = (root.isDirectory() ? new ExplodedArchive(root)
            : new JarFileArchive(root));

        List<Archive> archives = new ArrayList<>(
            archive.getNestedArchives(Context::isNestedArchive));

        List<URL> urls = new ArrayList<>(archives.size());
        for (Archive a : archives) {
            urls.add(a.getUrl());
        }

        Context ctx = new Context();
        ctx_ = ctx;

        ClassLoader cl = new LaunchedURLClassLoader(urls.toArray(new URL[0]),
            Context.class.getClassLoader());

        ClassLoader old = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(cl);

        trace("start: done");

        ServiceLoader<ServletContainerInitializer> initializers =
            ServiceLoader.load(ServletContainerInitializer.class, cl);

        ScanResult scan_res = new ClassGraph()
            .enableAllInfo()
            .scan();

        for (ServletContainerInitializer sci : initializers) {

            trace("start: initializer: " + sci.getClass().getName());

            HandlesTypes annotation = sci.getClass().getAnnotation(HandlesTypes.class);
            if (annotation == null) {
                continue;
            }

            Class<?>[] classes = annotation.value();
            if (classes == null) {
                continue;
            }

            Set<Class<?>> handles_classes = new HashSet<>();

            for (Class<?> c : classes) {
                trace("start: handles: " + c.getName());

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

                    trace("start: handles class: " + ci.getName());
                    handles_classes.add(ci.loadClass());
                }
            }

            if (handles_classes.isEmpty()) {
                continue;
            }

            try {
                sci.onStartup(handles_classes, ctx);
            } catch(Exception e) {
                System.err.println("start: exception caught: " + e.toString());
            }
        }

        ctx.initialized();

        Thread.currentThread().setContextClassLoader(old);
    }

    private class CtxFilterChain implements FilterChain
    {
        private int filter_index_ = 0;

        @Override
        public void doFilter (ServletRequest request, ServletResponse response) throws IOException, ServletException
        {
            if (filter_index_ < filters_.size()) {
                filters_.get(filter_index_++).filter_.doFilter(request, response, this);

                return;
            }

            for (CtxSRDynamic sr : servlets_ ) {
                sr.servlet_.service(request, response);
            }
        }
    }

    public static void service(ServletRequest req, ServletResponse resp) throws ServletException, IOException {
        ctx_.callServlets(req, resp);
    }

    public static Context getContext()
    {
        return ctx_;
    }

    private void callServlets(ServletRequest req, ServletResponse resp) throws ServletException, IOException {
        FilterChain fc = new CtxFilterChain();

        fc.doFilter(req, resp);
    }

    private class CtxInitParams {
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

    private class CtxReg extends CtxInitParams implements Registration {

        private String name_;
        private String class_name_;

        public CtxReg(String name, String class_name) {
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

    }

    private class CtxSRDynamic extends CtxReg implements ServletRegistration.Dynamic, ServletConfig {

        private Servlet servlet_;
        private String role_;
        private boolean async_supported_ = false;
        private final Set<String> patterns_ = new HashSet<String>();

        public CtxSRDynamic(String name, Servlet servlet) {
            super(name, servlet.getClass().getName());
            servlet_ = servlet;
        }

        @Override
        public Set<String> addMapping(String... urlPatterns)
        {
            // illegalStateIfContextStarted();
            Set<String> clash = null;
            for (String pattern : urlPatterns) {
                trace("CtxSRDynamic.addMapping: " + pattern);

                if (patterns_.contains(pattern)) {
                    if (clash == null)
                        clash = new HashSet<String>();
                    clash.add(pattern);
                }
            }

            //if there were any clashes amongst the urls, return them
            if (clash != null) {
                return clash;
            }

            for (String pattern : urlPatterns) {
                patterns_.add(pattern);
            }

            return Collections.emptySet();
        }

        @Override
        public Collection<String> getMappings()
        {
            trace("CtxSRDynamic.getMappings");
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
            log("CtxSRDynamic.setLoadOnStartup: " + loadOnStartup);
        }

        @Override
        public Set<String> setServletSecurity(ServletSecurityElement constraint)
        {
            log("CtxSRDynamic.setServletSecurity");
            return Collections.emptySet();
        }

        @Override
        public void setMultipartConfig(
            MultipartConfigElement multipartConfig)
        {
            log("CtxSRDynamic.setMultipartConfig");
        }

        @Override
        public void setRunAsRole(String roleName)
        {
            log("CtxSRDynamic.setRunAsRole: " + roleName);
            role_ = roleName;
        }

        @Override
        public void setAsyncSupported(boolean isAsyncSupported)
        {
            log("CtxSRDynamic.setAsyncSupported: " + isAsyncSupported);
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

    private class CtxFRDynamic extends CtxReg implements FilterRegistration.Dynamic, FilterConfig
    {
        private Filter filter_;
        private boolean async_supported_ = false;

        public CtxFRDynamic(String name, Filter filter)
        {
            super(name, filter.getClass().getName());
            filter_ = filter;
        }

        @Override
        public void addMappingForServletNames(
            EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter,
            String... servletNames)
        {
            for (String n : servletNames) {
                log("CtxFRDynamic.addMappingForServletNames: ... " + n);
            }
        }

        @Override
        public Collection<String> getServletNameMappings()
        {
            log("CtxFRDynamic.getServletNameMappings");
            return Collections.emptySet();
        }

        @Override
        public void addMappingForUrlPatterns(
            EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter,
            String... urlPatterns)
        {
            for (String u : urlPatterns) {
                log("CtxFRDynamic.addMappingForUrlPatterns: ... " + u);
            }
        }

        @Override
        public Collection<String> getUrlPatternMappings()
        {
            log("CtxFRDynamic.getUrlPatternMappings");
            return Collections.emptySet();
        }

        @Override
        public void setAsyncSupported(boolean isAsyncSupported)
        {
            log("CtxFRDynamic.setAsyncSupported: " + isAsyncSupported);
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

    public Context()
    {
    }

    public Context(String context_path)
    {
        context_path_ = context_path;
    }

    private void initialized()
    {
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

        for (CtxSRDynamic sr : servlets_) {
            try {
                sr.servlet_.init((ServletConfig) sr);
            } catch(ServletException e) {
                System.err.println("initialized: exception caught: " + e.toString());
            }
        }

        for (CtxFRDynamic fr : filters_) {
            try {
                fr.filter_.init((FilterConfig) fr);
            } catch(ServletException e) {
                System.err.println("initialized: exception caught: " + e.toString());
            }
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
        public void forward(ServletRequest request, ServletResponse response) throws ServletException, IOException
        {
            log("CtxRequestDispatcher.forward");
            Request r = (Request) request;

            r.set_forward_uri(uri_);

            for (CtxSRDynamic sr : servlets_ ) {
                sr.servlet_.service(request, response);
            }

            r.set_forward_uri(null);

            log("CtxRequestDispatcher.forward done");
        }

        @Override
        public void include(ServletRequest request, ServletResponse response) throws ServletException, IOException
        {
            log("CtxRequestDispatcher.include");

            callServlets(request, response);

            log("CtxRequestDispatcher.include done");
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
    public FilterRegistration.Dynamic addFilter(String filterName, Class<? extends Filter> filterClass)
    {
        log("addFilter<C> " + filterName + ", " + filterClass.getName());
        // LOG.warn(__unimplmented);
        return null;
    }

    private List<CtxFRDynamic> filters_ = new ArrayList<CtxFRDynamic>();

    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, Filter filter)
    {
        log("addFilter<F> " + filterName);

        CtxFRDynamic reg = new CtxFRDynamic(filterName, filter);
        filters_.add(reg);
        return reg;
    }

    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, String className)
    {
        log("addFilter<N> " + filterName + ", " + className);
        return null;
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass)
    {
        log("addServlet<C> " + servletName + ", " + servletClass.getName());
        return null;
    }

    private List<CtxSRDynamic> servlets_ = new ArrayList<CtxSRDynamic>();

    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet)
    {
        log("addServlet<S> " + servletName);

        CtxSRDynamic reg = null;
        try {
            reg = new CtxSRDynamic(servletName, servlet);
            servlets_.add(reg);
        } catch(Exception e) {
            System.err.println("addServlet: exception caught: " + e.toString());
        }

        return reg;
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, String className)
    {
        log("addServlet<N> " + servletName + ", " + className);
        return null;
    }

    @Override
    public <T extends Filter> T createFilter(Class<T> c) throws ServletException
    {
        log("createFilter<C> " + c.getName());
        return null;
    }

    @Override
    public <T extends Servlet> T createServlet(Class<T> c) throws ServletException
    {
        log("createServlet<C> " + c.getName());
        return null;
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
        return null;
    }

    @Override
    public Map<String, ? extends FilterRegistration> getFilterRegistrations()
    {
        log("getFilterRegistrations");
        //LOG.warn(__unimplmented);
        return null;
    }

    @Override
    public ServletRegistration getServletRegistration(String servletName)
    {
        log("getServletRegistration " + servletName);
        //LOG.warn(__unimplmented);
        return null;
    }

    @Override
    public Map<String, ? extends ServletRegistration> getServletRegistrations()
    {
        log("getServletRegistrations");
        //LOG.warn(__unimplmented);
        return null;
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

    @Override
    public void addListener(String className)
    {
        log("addListener<N> " + className);
        //LOG.warn(__unimplmented);
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
        //LOG.warn(__unimplmented);
    }

    @Override
    public void addListener(Class<? extends EventListener> listenerClass)
    {
        log("addListener<C> " + listenerClass.getName());
        //LOG.warn(__unimplmented);
    }

    @Override
    public <T extends EventListener> T createListener(Class<T> clazz) throws ServletException
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
        return Context.class.getClassLoader();
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
