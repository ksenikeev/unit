package nginx.unit;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import java.io.Serializable;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Andrey Kazankov
 */
public class Session implements HttpSession, Serializable
{
    private final Map<String, Object> attributes = new HashMap<>();
    private final long creation_time = new Date().getTime();
    private long last_access_time = 0;
    private long access_time = creation_time;
    private String id;
    private final Context context;
    private boolean is_new = true;

    public Session(Context context, String id)
    {
        this.id = id;
        this.context = context;
    }

    public void setId(String id)
    {
        this.id = id;
    }

    @Override
    public long getCreationTime()
    {
        return creation_time;
    }

    @Override
    public String getId()
    {
        return id;
    }

    @Override
    public long getLastAccessedTime()
    {
        return last_access_time;
    }

    @Override
    public ServletContext getServletContext()
    {
        return context;
    }

    @Override
    public void setMaxInactiveInterval(int i)
    {

    }

    @Override
    public int getMaxInactiveInterval()
    {
        return 0;
    }

    @Deprecated
    @Override
    public javax.servlet.http.HttpSessionContext getSessionContext()
    {
        return null;
    }

    @Override
    public Object getAttribute(String s)
    {
        return attributes.get(s);
    }

    @Deprecated
    @Override
    public Object getValue(String s)
    {
        return getAttribute(s);
    }

    @Override
    public Enumeration<String> getAttributeNames()
    {
        return Collections.enumeration(attributes.keySet());
    }

    @Deprecated
    @Override
    public String[] getValueNames()
    {
        return attributes.keySet().toArray(new String[attributes.keySet().size()]);
    }

    @Override
    public void setAttribute(String s, Object o)
    {
        attributes.put(s,o);
    }

    @Deprecated
    @Override
    public void putValue(String s, Object o)
    {
        setAttribute(s,o);
    }

    @Override
    public void removeAttribute(String s)
    {
        attributes.remove(s);
    }

    @Deprecated
    @Override
    public void removeValue(String s)
    {
        removeAttribute(s);
    }


    @Override
    public void invalidate()
    {
        context.invalidateSession(this);
    }

    @Override
    public boolean isNew()
    {
        return is_new;
    }

    public void accessed() {
        is_new = false;

        last_access_time = access_time;
        access_time = new Date().getTime();
    }
}
