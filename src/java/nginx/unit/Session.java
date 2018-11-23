package nginx.unit;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;
import java.io.Serializable;
import java.util.*;

/**
 * @author Andrey Kazankov
 */
public class Session implements HttpSession, Serializable {

    private Map<String, Object> attributesMap = new HashMap<>();
    private long creationTime = new Date().getTime();
    private String id;
    private Context context;
    boolean isNewSession = false;

    public Session(Context context)
    {
        SessionIdGenerator generator =  new UUIDSessionIdGenerator();
        generator.init();
        this.id = generator.generate();
        isNewSession = true;
        this.context = context;
    }


    public Session(SessionIdGenerator idGenerator, Context context)
    {
        this.id = idGenerator.generate();
        this.context = context;
    }

    @Override
    public long getCreationTime()
    {
        return creationTime;
    }

    @Override
    public String getId()
    {
        return  id;
    }

    @Override
    public long getLastAccessedTime()
    {
        return 0;
    }

    @Override
    public ServletContext getServletContext()
    {
        return null;
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

    @Override
    public HttpSessionContext getSessionContext()
    {
        return null;
    }

    @Override
    public Object getAttribute(String s)
    {
        return attributesMap.get(s);
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
        return Collections.enumeration(attributesMap.keySet());
    }

    @Deprecated
    @Override
    public String[] getValueNames()
    {
        //(String[]) Collections.list(getAttributeNames()).toArray()
        return attributesMap.keySet().toArray(new String[attributesMap.keySet().size()]);
    }

    @Override
    public void setAttribute(String s, Object o)
    {
        attributesMap.put(s,o);
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
        attributesMap.remove(s);
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
        context.getSessionManager().invalidate(this);
    }

    @Override
    public boolean isNew()
    {
        return isNewSession;
    }

    public void setIsNewSession(boolean b) {
        isNewSession = b;
    }

    public interface SessionIdGenerator
    {
        void init();
        String generate();
    }


    public class UUIDSessionIdGenerator implements SessionIdGenerator
    {

        @Override
        public void init()
        {
            // initialization id generator
        }

        @Override
        public String generate()
        {
            return UUID.randomUUID().toString();
        }
    }
}
