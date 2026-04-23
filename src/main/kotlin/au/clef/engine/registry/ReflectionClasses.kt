package au.clef.engine.registry

interface ReflectionClasses : RegisteredClasses {

    /**
     * Classes of static methods or class od an instance that if the facade that will be invoked. Eg Math.min(int,int)
     * or instance of a service val myService = MyService() - with class MyService::class
     */
    val targetClasses: List<Class<*>>
}