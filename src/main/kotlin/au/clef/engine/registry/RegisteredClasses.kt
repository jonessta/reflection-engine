package au.clef.engine.registry

interface RegisteredClasses {

    /**
     * All classes supporting reflection, including target classes. Supporting classes are method composite structures
     * like Person class in the following:
     * myService = MyService()
     * val person1 = new Person("David", 23, MALE)
     * myService.addPerson(person1)
     */
    val classes: List<Class<*>>
}