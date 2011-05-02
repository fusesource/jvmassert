# The JvmAssert Scala Compiler Plugin

This is Scala compiler plugin which enables your Scala assert statements only 
when the JVM `-ea` runtime option is used on your JVM.

## Source Code

The source code for this project is maintained at:

http://github.com/fusesource/jvmassert

## What does it actually do?

Lets say you have simple class that is use the Scala assert method:


    class Example {
      def test = {
        assert( (1+1) == 2, "Addition broken")
      }
    }

When you enable the `jvmassert` compiler plugin, it will translate the
previous example to:

    object Example {
      val $enable_assertions = getClass.desiredAssertionStatus
    }
    class Example {
      def test = {
        if( Example.$enable_assertions ) {
          assert( (1+1) == 2, "Addition broken")
        }
      }
    }

## Known Issues

This plugin `if` wraps any method call who's method name
is `assert`!  So don't enable this plugin if you have your
calling other assert methods.
