package nginx.unit;

public interface DynamicPathRequest
    extends DynamicDispatcherRequest
{
    public void setServletPath(String path);

    public void setPathInfo(String path);

    public void setRequestURI(String uri);
}
