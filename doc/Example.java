package com.warburg.somelang;

// data Foo = Bar
public abstract class Foo {
    private Foo() {}
    // TODO can't make inner class have the same name... any workaround?
    public final static class Bar extends Foo {
        public static final Bar INSTANCE = new Bar();
        private Bar() {}
    }
}

// data Foo2 = Bar | Baz
abstract class Foo2 {
    private Foo2() {}
    final static class Bar extends Foo2 {
        public static final Bar INSTANCE = new Bar();
        private Bar() {}
    }
    final static class Baz extends Foo2 {
        public static final Baz INSTANCE = new Baz();
        private Baz() {}
    }
}

// data Foo3 = Bar Int
abstract class Foo3 {
    private Foo3() {}
    final static class Bar extends Foo3 {
        final int component1;

        public Bar(final int component1) {
            this.component1 = component1;
        }
    }
}

// data Foo4 a = Bar a
abstract class Foo4<A> {
    private Foo4() {}
    final static class Bar<A> extends Foo4<A> {
        final A component1;

        public Bar(final A component1) {
            this.component1 = component1;
        }
    }
}

interface Provider<I> {
    I getImpl();
}

// trait Show a {
//     fun show : a -> String
// }
interface Show<A> {
    String show(A a);

    static <A> String show(A a, Show<A> impl) {
        return impl.show(a);
    }

    static <A extends ShowProvider<A>> String show(A a) {
        return Show.show(a, a.getShowImpl());
    }
}

interface ShowProvider<A> {
    Show<A> getShowImpl();
}

class CoolJavaClass implements ShowProvider<CoolJavaClass> {
    private final String theString;

    CoolJavaClass(final String theString) {
        this.theString = theString;
    }

    @Override
    public String toString() {
        return this.theString;
    }

    // TODO reduce boilerplate for java implementers somehow.
    @Override
    public Show<CoolJavaClass> getShowImpl() {
        return ShowIt.INSTANCE;
    }

    private static class ShowIt implements Show<CoolJavaClass> {
        private static final ShowIt INSTANCE = new ShowIt();
        @Override
        public String show(CoolJavaClass javaObj) {
            return javaObj.toString();
        }
    }
}

class ExampleWithCoolJavaClass {
    static String doStuff() {
        return Show.show(new CoolJavaClass("abc"));
    }
}

class NoMatchException extends RuntimeException {}

// data Foo5 = Bar String
// impl Show Foo5 {
//     fun show : (foo: Foo5) -> String = match foo {
//         Bar s -> s
//     }
// }
abstract class Foo5 {
    private Foo5() {}
    final static class Bar extends Foo5 {
        final String component1;

        public Bar(final String component1) {
            this.component1 = component1;
        }
    }
}
final class ShowFoo5 implements Show<Foo5> {
    private ShowFoo5() {}
    public static final Show<Foo5> INSTANCE = new ShowFoo5();

    @Override
    public String show(final Foo5 foo5) {
        if (foo5 instanceof Foo5.Bar) {
            final String s = ((Foo5.Bar) foo5).component1;
            return s;
        }

        throw new NoMatchException();
    }
}

// fun showIt : String = show (Bar "abc")
final class TheContainingFileName {
    private TheContainingFileName() {}

    public static String showIt() {
        // TODO nicer way to expose this to java, so it doesn't have to pass around trait instances all the time?
        return Show.show(new Foo5.Bar("abc"), ShowFoo5.INSTANCE);
    }
}
