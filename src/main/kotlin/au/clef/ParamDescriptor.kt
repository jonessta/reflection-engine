package au.clef

 class ParamDescriptor(
     val index: Int,
    val type: Class<*>,
    val nullable: Boolean
) {
     override fun toString(): String {
         return "ParamDescriptor(name='$index', type=$type, nullable=$nullable)"
     }
 }