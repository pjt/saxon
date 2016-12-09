Saxon
=====

Functional Clojure wrapper for Michael Kay's
[Saxon XSLT and XQuery Processor][saxonica] (from Saxonica Limited).

Install
-------

A jar is available on [Clojars](http://clojars.org). Use the following formula
in your `project.clj` `:dependencies`:

    [clojure-saxon "0.9.4"]

Clojure version
---------------

The library has been tested against Clojure 1.3, 1.4, & 1.6.


Use
---

You must use a Saxon Processor to perform Saxon operations and XML parsing.
The Saxon Processor is thread-safe, so you may be able to use one, as long as your
application does not require multiple Saxon Configurations.

    user=> (require '[saxon :as s])
    nil
    (def proc (s/processor))
    #'user/proc

You can configure a saxon parser by passing in a configuration map directly:

    (def proc (s/processor {:allow-multithreading true}))
    #'user/proc

You can also configure a saxon parser by using any number of keyword arguments:

    (def proc (s/processor :allow-multithreading true))
    #'user/proc

###Query

The top-level function is `query`; it takes a processor, an XQuery or XPath expression, an
optional namespace map, and a node. Here's returning a sequence of the names of
all elements in a document:

    user=> (s/query proc "distinct-values(//element()/local-name())" xmldoc)
    ("html" "head" "title" "style" "body" "p" "a")

Here's the same, limiting elements to those in the XHTML namespace:

    user=> (s/query proc "distinct-values(//xhtml:*/local-name())" {:xhtml "http://www.w3.org/1999/xhtml"} xmldoc)
    ("html" "head" "title" "style" "body" "p" "a")

Compiled XQuery expressions are cached:

    user=> (def complex-exp "//element()[@type='text/css']//local-name(parent::element()) = 'head'")
    #'user/complex-exp
    user=> (time (s/query proc complex-exp xmldoc))
    "Elapsed time: 1.747 msecs"
    true
    user=> (time (s/query proc complex-exp xmldoc))
    "Elapsed time: 0.226 msecs"
    true

but `query` accepts a compiled function (the result of `compile-xquery` or `compile-xpath`,
below) as its first argument as well. (The *results* of the query are not cached.)


###Compile-xquery, Compile-xpath

`compile-xquery` and `compile-xpath` are the lower-level functions behind `query`.
They take expressions and an optional namespace map, and return a function that applies
the compiled expression to a node.

    user=> (s/compile-xquery proc "distinct-values(//element()/local-name())")
    #<saxon$compile_xquery__302$fn__314 saxon$compile_xquery__302$fn__314@48c5186e>
    user=> ((s/compile-xquery proc "distinct-values(//element()/local-name())") xmldoc)
    ("html" "head" "title" "style" "body" "p" "a")

`compile-xquery` and `compile-xpath` cache their query arguments as well.

###Compiling XML

Use `compile-xml` to produce the Saxon in-memory representation, an "XdmNode."

    user=> (def xmldoc (s/compile-xml proc (java.net.URL. "http://hdwdev.artsci.wustl.edu")))
    #'user/xmldoc

`compile-xml` takes a File, URL, InputStream, Reader, raw String, or an XdmNode.

    user=> (s/compile-xml proc "<root/>")
    #<XdmNode <root/>>


###XSLT

`compile-xslt` takes the same arguments as `compile-xml` and returns a function
that applies the compiled stylesheet to a node, with an optional map of parameters.


###Singletons

When the result of a query is a single item, the query functions return a singleton
instead of a sequence of one item, e.g.

    user=> (s/query proc "count(//element())" xmldoc)
    7

I find this inconsistency convenient, but it might be a bad design choice. User feedback
appreciated.


###Laziness

Traversal of nodes is somewhat lazy, though not strictly so. The Clojure code
realizes the first two items of the return sequence, and the Saxon Java processor
seems to keep a few items ahead as well. E.g. in `xmldoc`, with 7 element nodes:

    user=> (def returned (s/query "for $e in //element() return trace(($e/local-name()), \"hit\")" xmldoc))
    hit [1]: xs:string: html
    hit [1]: xs:string: head
    hit [1]: xs:string: title
    #'user/returned
    user=> (nth returned 0)
    "html"
    user=> (nth returned 1)
    "head"
    user=> (nth returned 2)
    hit [1]: xs:string: style
    "title"
    user=> (nth returned 3)
    hit [1]: xs:string: body
    "style"
    user=> (nth returned 4)
    hit [1]: xs:string: p
    "body"
    user=> (nth returned 5)
    hit [1]: xs:string: a
    "p"
    user=> (nth returned 6)
    "a"

As you can see, three items are realized when the function is first executed, and
from when the third item is touched onward, realizing an item also realizes the
*next* item in the background.


###Helper Functions

`node-path` returns an absolute XPath to a node:

    user=> (map s/node-path (s/query "//element()" xmldoc))
    ("/html" "/html/head[1]" "/html/head[1]/title[1]" "/html/head[1]/style[1]" "/html/body[1]" "/html/body[1]/p[1]" "/html/body[1]/a[1]")

`with-default-ns` adds a default namespace to an XQuery expression:

    user=> (s/query (s/with-default-ns "http://www.w3.org/1999/xhtml" "//*/local-name()") xmldoc)
    ("html" "head" "title" "style" "body" "p" "a")



   [saxonica]: http://saxonica.com/
   [sfdl]: http://sourceforge.net/project/showfiles.php?group_id=29872&package_id=21888
