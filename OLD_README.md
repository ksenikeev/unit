## NGINX Unit with Java Support

> _Abandon all hope, ye who enter here_

#### Synopsys

```bash
git clone -b java https://github.com/mar0x/unit.git
cd unit
./configure
./configure java
make
```

#### Config sample

```json
{
    "listeners": {
        "127.0.0.1:8080": {
            "application": "java-web-app"
        }
    },

    "applications": {
        "java-web-app": {
            "type": "java",
            "processes": 1,
            "webapp": "app.war",
        }
    }
}
```

## NGINX Unit

The documentation and binary packages are available online:

  http://unit.nginx.org

The source code is provided under the terms of Apache License 2.0:

  http://hg.nginx.org/unit

Please ask questions, report issues, and send patches to the mailing list:

  unit@nginx.org (http://mailman.nginx.org/mailman/listinfo/unit)

or via Github:

  https://github.com/nginx/unit

--
NGINX, Inc.
http://nginx.com


