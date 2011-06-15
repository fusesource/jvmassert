# The JvmAssert Scala Compiler Plugin

This is Scala compiler plugin which enables your Scala assert statements only 
when the JVM `-ea` runtime option is used on your JVM.

## Source Code

The source code for this project is maintained at:

http://github.com/fusesource/jvmassert

## What does it actually do?

Lets say you have simple class that is using the Scala assert method:


    class Example {
      def test = {
        assert( (1+1) == 2, "Addition broken")
      }
    }

When you enable the `jvmassert` compiler plugin, it will translate the
previous example to:

    object Example {
      final val $enable_assertions = getClass.desiredAssertionStatus
    }
    class Example {
      def test = {
        if( Example.$enable_assertions ) {
          assert( (1+1) == 2, "Addition broken")
        }
      }
    }
    
## Automatic Assertion Message 

If you don't give the assertion an explicit message, the plugin 
will generate the message for you based on the expression.  For
example if you run the following example:

    object Example {
      def main(args:Array[String]) = {
        val actual = 2
        val expected = 3
        assert( actual == expected )
      }
    }

Then you will get the an error message like:

    Exception in thread "main" java.lang.AssertionError: assertion failed: actual.$eq$eq(expected) with actual=>2, expected=>3
      at scala.Predef$.assert(Predef.scala:103)
      at Example$.main(t.scala:5)
      at Example.main(t.scala)

## Adding to Your Maven Based Build

Just add the configuration to your `maven-scala-plugin`:

    <configuration>
      ...
      <compilerPlugins>
        <compilerPlugin>
          <groupId>org.fusesource.jvmassert</groupId>
          <artifactId>jvmassert</artifactId>
          <version>1.2</version>
        </compilerPlugin>
      </compilerPlugins>
      ..
    </configuration>
    
## Known Issues

This plugin `if` wraps any method call who's method name
is `assert`!  So don't enable this plugin if you have your
calling other assert methods.
