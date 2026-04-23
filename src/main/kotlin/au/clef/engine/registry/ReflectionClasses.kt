package au.clef.engine.registry

// todo does this extend RegisteredClasses
interface ReflectionClasses {

    /**
     * Classes of static methods or class od an instance that if the facade that will be invoked. Eg Math.min(int,int)
     * or instance of a service val myService = MyService() - with class MyService::class
     */
    val targetClasses: List<Class<*>>

    /**
     * All classes supporting reflection, including target classes. Supporting classes are method composite structures
     * like Person class in the following:
     * myService = MyService()
     * val person1 = new Person("David", 23, MALE)
     * myService.addPerson(person1)
     */
    val classes: List<Class<*>>
}