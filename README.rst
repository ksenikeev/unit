###########################################
Obtain And Configure Unit with Java Support
###########################################

As of December 2018, Unit's support for Java is in technical preview / beta phase.
Currently, you can try the code directly from this repository:

.. code-block:: console

    # git clone -b java https://github.com/mar0x/unit.git
    # cd unit
    # ./configure --prefix=/home/user/unit_local_setup

Refer to General Unit Guide for ./configure details:
http://unit.nginx.org/installation/#configuring-sources

.. code-block:: console

    # ./configure java <options>

Available command options:

--home=directory
    Directory path for Java utilities and header files (required to build the
    module).

    The default value is the ``java.home`` setting.

--lib-path=directory
    Directory path for the ``libjvm.so`` library.

    The default value is derived from JDK settings.

--local-repo=directory
    Directory path for local ``.jar`` repository.

    The default value is ``$HOME/.m2/repository/``.

--repo=directory
    URL path for remote Maven repository.

    The default value is http://central.maven.org/maven2/.

--module=filename
    Target name for the Java module that Unit will build
    (``<module>.unit.so``). Also used for build and install commands.

    The default value is ``java``.

To configure a module called ``java11.unit.so`` with OpenJDK 11.0.1:

.. code-block:: console

    # ./configure java --module=java11 \
        --home=/Library/Java/JavaVirtualMachines/jdk-11.0.1.jdk/Contents/Home

######################
Build and Install Unit
######################

To build Unit and language modules that you have configured and install them:

.. code-block:: console

    # make
    # make install

###########
Launch Unit
###########

For details, see here: http://unit.nginx.org/installation/#startup

###############################
Configure Your Java Application
###############################

Create your app’s entry as http://localhost/config/applications/*<app name>*:
http://unit.nginx.org/configuration/#example-create-an-application-object

Next, create a listener at http://localhost/config/listeners/*<IP:port>*:
http://unit.nginx.org/configuration/#listeners

See a full config sample here: http://unit.nginx.org/configuration/#full-example

Java Application Options
########################

.. list-table::
   :header-rows: 1

   * - Object
     - Description

   * - ``classpath``
     - Array of string values containing your application’s custom class paths
       (may point to directories or .jar files; not to be confused with the
       -classpath JVM option).

   * - ``options``
     - Array of string values containing JVM runtime options.

   * - ``webapp``
     - Pathname of the application’s packaged or unpackaged ``.war`` file.

Basic Java application config:

.. code-block:: json

    {
        "type": "java",
        "webapp": "/www/qwk2mart/qwk2mart.war"
    }

Finally, access your app at the listener’s IP address and port, i.e.:

.. code-block:: console

    # curl http://127.0.0.1:8080

Enjoy your brew and the upcoming holidays.
