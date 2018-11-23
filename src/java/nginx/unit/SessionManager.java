package nginx.unit;

import org.eclipse.jetty.util.StringUtil;

import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.swing.Timer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

/**
 * @author Andrey Kazankov
 */
public class SessionManager implements ActionListener {

    private SessionCookieConfig sessionCookieConfig = new UnitSessionCookieConfig();

    private int cleanerTimeOut = 30_000;

    private Timer cleaner = new Timer(cleanerTimeOut, this);

    public static final String JUNIT_SESSION_ID_COOKIE_NAME = "JSESSIONID";

    private Map<String, HttpSession> map = new HashMap<>();

    private Set<SessionTrackingMode> sessionTrackingModes;

    Context context;

    private int sessionTimeOut = 60*60*1000;

    public SessionManager(Context context)
    {
        this.context = context;
        //cleaner.start();
        sessionTrackingModes = new HashSet<SessionTrackingMode>();
        sessionTrackingModes.add(SessionTrackingMode.COOKIE);

    }

    public SessionCookieConfig getSessionCookieConfig()
    {
        return sessionCookieConfig;
    }

    public void setSessionCookieConfig(SessionCookieConfig sessionCookieConfig)
    {
        this.sessionCookieConfig = sessionCookieConfig;
    }

    public HttpSession getSession(boolean create, HttpServletRequest request, HttpServletResponse response)
    {

        boolean isNeedSetCookie = false;
        trace("getSession()");

        Cookie cookie = getSessionIdCookie(request);
        HttpSession result = null;

        if(cookie!=null)
        {
            trace("cookie found "+cookie.getValue());
            result = map.get(cookie.getValue());

            if(result!=null) {
                trace("session found");
                ((Session)result).setIsNewSession(false);
            }
            else {
                trace("session not found");

                if(create) {
                    result = new Session(context);
                    map.put(result.getId(), result);
                    isNeedSetCookie = true;
                }
            }
        }
        else
        {
            trace("cookie not found");

            if(create) {
                result = new Session(context);
                map.put(result.getId(), result);
                isNeedSetCookie = true;
                trace(map.get(result.getId())!=null?map.get(result.getId()).getId():"map empty");
                trace("but session must be created");
                trace("session = new Session()" +result.getId());
            }
        }

        if (isNeedSetCookie)
            addSessionCookie(result.getId(), (Response)response);

        /*
        if(result!=null && result.getCreationTime() + cleanerTimeOut < new Date().getTime()){ //если у найденной сессии истекло "время жизни"
            map.remove(result); //забываем про нее на сервере
            result = null; //возвращаем null
        }
        */
        return result;
    }

    private void addSessionCookie(String value, Response response)
    {
        Cookie c = new Cookie(sessionCookieConfig.getName(), value);
        c.setComment(sessionCookieConfig.getComment());
        if (!StringUtil.isBlank(sessionCookieConfig.getDomain()))
            c.setDomain(sessionCookieConfig.getDomain());
        c.setHttpOnly(sessionCookieConfig.isHttpOnly());
        if (!StringUtil.isBlank(sessionCookieConfig.getPath()))
            c.setPath(sessionCookieConfig.getPath());
        c.setMaxAge(sessionCookieConfig.getMaxAge());
        response.addCookie(c);
    }

    private Cookie getSessionIdCookie(HttpServletRequest request)
    {
        Cookie cookie = null;
        Cookie[] cookies = request.getCookies();
        if(cookies!=null)
        {
            trace("cookies found! count = "+cookies.length);
            for (Cookie next : cookies) {
                trace("finding cookie...");
                if (next.getName().equals(JUNIT_SESSION_ID_COOKIE_NAME)) {
                    cookie = next;
                    break;
                }
            }
        }
        else {
            System.out.println("cookies not found.");
        }
        return cookie;
    }

    public void invalidate(Session session)
    {
        map.remove(session.getId());
    }


    @Override
    public void actionPerformed(ActionEvent e)
    {
        trace("session count = "+map.size());
        trace("cleaning...");
        long now = new Date().getTime();
        map.values().removeIf(httpSession -> httpSession.getCreationTime() + cleanerTimeOut >  now);
        trace("now  session count = "+map.size());
    }


    public int getCleanerTimeOut()
    {
        return cleanerTimeOut;
    }

    public void setCleanerTimeOut(int cleanerTimeOut)
    {
        this.cleanerTimeOut = cleanerTimeOut;
        cleaner = new Timer(cleanerTimeOut, this);
        cleaner.start();
    }

    public Set<SessionTrackingMode> getDefaultSessionTrackingModes()
    {
        Set<SessionTrackingMode> res = new HashSet<SessionTrackingMode>();
        res.add(SessionTrackingMode.COOKIE);
        return res;
    }

    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes()
    {
        return sessionTrackingModes;
    }

    public void trace(String msg)
    {
        //trace("SessionManager: "+msg);
    }

    public synchronized void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes)
    {
         this.sessionTrackingModes = sessionTrackingModes;
    }

    public int getSessionTimeOut()
    {
        return sessionTimeOut;
    }

    public void setSessionTimeOut(int sessionTimeOut)
    {
        this.sessionTimeOut = sessionTimeOut;
    }
}
