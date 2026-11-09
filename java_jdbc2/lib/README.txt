MySQL Connector/J dependency
============================

Download the MySQL Connector/J platform-independent ZIP from the official MySQL
Connector/J downloads page, extract it, and copy the connector JAR into this
directory. A typical filename is mysql-connector-j-9.x.x.jar; use the exact
filename in the compile/run commands in the project README.

The driver is loaded at runtime, so the Java sources compile without this JAR.
The JAR is required on the classpath to connect to MySQL. Do not commit the JAR
unless your course/project distribution rules allow it.
