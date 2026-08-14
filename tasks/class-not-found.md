
# Step to reproduce
1. open preview on /Users/hawk/Documents/workspace/zkdemo/src/main/webapp/widgets/listbox/paging/paging.zul


# Current result

org.zkoss.zk.ui.UiException: Sourced file: inline evaluation of: ``  		List items = new demo.data.BigList(1000); //a big list of Integer 	;'' : Typed variable declaration : Class or variable not found: demo.data.BigList : at Line: 3 : in file: inline evaluation of: ``  		List items = new demo.data.BigList(1000); //a big list of Integer 	;'' : demo .data .BigList 

	at java.base/jdk.internal.reflect.DirectConstructorHandleAccessor.newInstance(DirectConstructorHandleAccessor.java:62)
	at java.base/java.lang.reflect.Constructor.newInstanceWithCaller(Constructor.java:499)
	at java.base/java.lang.reflect.Constructor.newInstance(Constructor.java:483)
	at zk-preview-scoped//org.zkoss.lang.Classes.newInstance(Classes.java:77)
	at zk-preview-scoped//org.zkoss.lang.Exceptions.wrap(Exceptions.java:164)
	at zk-preview-scoped//org.zkoss.zk.ui.UiException$Aide.wrap(UiException.java:51)
	at zk-preview-scoped//org.zkoss.zk.scripting.bsh.BSHInterpreter.exec(BSHInterpreter.java:136)
	at zk-preview-scoped//org.zkoss.zk.scripting.util.GenericInterpreter.interpret(GenericInterpreter.java:343)
	at zk-preview-scoped//org.zkoss.zk.ui.impl.PageImpl.interpret(PageImpl.java:969)
	at zk-preview-scoped//org.zkoss.zk.ui.impl.UiEngineImpl.execNonComponent(UiEngineImpl.java:1084)
	at zk-preview-scoped//org.zkoss.zk.ui.impl.UiEngineImpl.execCreate0(UiEngineImpl.java:837)
	at zk-preview-scoped//org.zkoss.zk.ui.impl.UiEngineImpl.execCreateChild(UiEngineImpl.java:847)
	at zk-preview-scoped//org.zkoss.zk.ui.impl.UiEngineImpl.execCreate0(UiEngineImpl.java:805)
	at zk-preview-scoped//org.zkoss.zk.ui.impl.UiEngineImpl.execCreate(UiEngineImpl.java:751)
	at zk-preview-scoped//org.zkoss.zk.ui.impl.UiEngineImpl.execNewPage0(UiEngineImpl.java:469)
	at zk-preview-scoped//org.zkoss.zk.ui.impl.UiEngineImpl.execNewPage(UiEngineImpl.java:377)
	at zk-preview-scoped//org.zkoss.zk.ui.http.DHtmlLayoutServlet.process(DHtmlLayoutServlet.java:224)
	at zk-preview-scoped//org.zkoss.zk.ui.http.DHtmlLayoutServlet.doGet(DHtmlLayoutServlet.java:141)
	at javax.servlet.http.HttpServlet.service(HttpServlet.java:645)
	at javax.servlet.http.HttpServlet.service(HttpServlet.java:750)
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:104)
	at java.base/java.lang.reflect.Method.invoke(Method.java:565)
	at org.zkoss.zkpreview.AbstractRenderEngine.renderZul(AbstractRenderEngine.java:103)
	at org.zkoss.zkpreview.PreviewHttpServer.handle(PreviewHttpServer.java:102)
	at jdk.httpserver/com.sun.net.httpserver.Filter$Chain.doFilter(Filter.java:98)
	at jdk.httpserver/sun.net.httpserver.AuthFilter.doFilter(AuthFilter.java:76)
	at jdk.httpserver/com.sun.net.httpserver.Filter$Chain.doFilter(Filter.java:101)
	at jdk.httpserver/sun.net.httpserver.ServerImpl$Exchange$LinkHandler.handle(ServerImpl.java:915)
	at jdk.httpserver/com.sun.net.httpserver.Filter$Chain.doFilter(Filter.java:98)
	at jdk.httpserver/sun.net.httpserver.ServerImpl$Exchange.run(ServerImpl.java:891)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1090)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:614)
	at java.base/java.lang.Thread.run(Thread.java:1474)
Caused by: Sourced file: inline evaluation of: ``  		List items = new demo.data.BigList(1000); //a big list of Integer 	;'' : Typed variable declaration : Class or variable not found: demo.data.BigList : at Line: 3 : in file: inline evaluation of: ``  		List items = new demo.data.BigList(1000); //a big list of Integer 	;'' : demo .data .BigList 

	at zk-preview-scoped//bsh.UtilEvalError.toEvalError(UtilEvalError.java:79)
	at zk-preview-scoped//bsh.UtilEvalError.toEvalError(UtilEvalError.java:84)
	at zk-preview-scoped//bsh.BSHAmbiguousName.toObject(BSHAmbiguousName.java:58)
	at zk-preview-scoped//bsh.BSHAllocationExpression.objectAllocation(BSHAllocationExpression.java:80)
	at zk-preview-scoped//bsh.BSHAllocationExpression.eval(BSHAllocationExpression.java:56)
	at zk-preview-scoped//bsh.BSHPrimaryExpression.eval(BSHPrimaryExpression.java:96)
	at zk-preview-scoped//bsh.BSHPrimaryExpression.eval(BSHPrimaryExpression.java:41)
	at zk-preview-scoped//bsh.BSHVariableDeclarator.eval(BSHVariableDeclarator.java:80)
	at zk-preview-scoped//bsh.BSHTypedVariableDeclaration.eval(BSHTypedVariableDeclaration.java:78)
	at zk-preview-scoped//bsh.Interpreter.eval(Interpreter.java:659)
	at zk-preview-scoped//bsh.Interpreter.eval(Interpreter.java:750)
	at zk-preview-scoped//org.zkoss.zk.scripting.bsh.BSHInterpreter.exec(BSHInterpreter.java:132)
	... 26 more

# Expected result
zk plugin finds the class and render a preview screen successfully.

# Debug info
the class, demo.data.BigList, actually exists in another source path :
/Users/hawk/Documents/workspace/zkdemo/src/main/webapp/source

which is also configured in the module setting.
I assume you can find the module setting in the project /Users/hawk/Documents/workspace/zkdemo/