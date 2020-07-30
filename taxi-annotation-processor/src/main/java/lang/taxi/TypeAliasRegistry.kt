package lang.taxi

import lang.taxi.kapt.KotlinTypeAlias
import org.reflections8.Reflections
import org.reflections8.scanners.SubTypesScanner
import org.reflections8.util.ClasspathHelper
import org.reflections8.util.ConfigurationBuilder
import org.slf4j.LoggerFactory
import kotlin.reflect.*

interface TypeAliasRegistrar {
   fun register()
}

object TypeAliasRegistry {
   private val log = LoggerFactory.getLogger(TypeAliasRegistry::class.java)
   private val _aliases = mutableMapOf<String, KotlinTypeAlias>()

   @JvmStatic
   fun register(typeAlias: KotlinTypeAlias) {
      System.out.println("Registering type alias ${typeAlias.qualifiedName}")
      _aliases.put(typeAlias.qualifiedName, typeAlias)
   }

   @JvmStatic
   fun register(vararg registrars: Class<out TypeAliasRegistrar>) {
      doRegister(registrars.toList())
   }

   @JvmStatic
   fun register(vararg registrars: KClass<out TypeAliasRegistrar>) {
      register(registrars.toList())
   }

   @JvmStatic
   fun scanAndRegister(basePackageClass:Class<*>) {
      val reflections = Reflections(ConfigurationBuilder()
         .setUrls(ClasspathHelper.forClass(basePackageClass))
         .setScanners(SubTypesScanner())
      )
//      val reflections = if (basePackage != null) Reflections(basePackage, SubTypesScanner()) else Reflections(SubTypesScanner())
      val registrars = reflections.getSubTypesOf(TypeAliasRegistrar::class.java)
      log.info("Scan found ${registrars.size} type alias registrars")
      doRegister(registrars.toList())
   }


   @JvmStatic
   fun register(registrars: List<KClass<out TypeAliasRegistrar>>) {
      doRegister(registrars.map { it.java })
   }

   @JvmStatic
   private fun doRegister(registrars: List<Class<out TypeAliasRegistrar>>) {
      registrars.forEach {
         val instance = it.newInstance()
         instance.register()
      }
   }

   fun findTypeAlias(kotlinType: KCallable<*>?): KotlinTypeAlias? {
      if (kotlinType == null) return null
      return when (kotlinType) {
         is KProperty<*> -> AliasHunter.findTypeAlias(kotlinType)
         else -> null
      }
   }

   fun findTypeAlias(parameter: KParameter?): KotlinTypeAlias? {
      return AliasHunter.findTypeAlias(parameter)
   }

   fun findTypeAlias(type: KType): KotlinTypeAlias? {
      return AliasHunter.findTypeAlias(type)
   }

   fun getAlias(qualifiedTypeAliasName: String): KotlinTypeAlias {
      return findAlias(qualifiedTypeAliasName) ?: error("No type alias registered with name $qualifiedTypeAliasName")
   }

   fun findAlias(qualifiedTypeAliasName: String): KotlinTypeAlias? {
      return _aliases[qualifiedTypeAliasName]
   }

   fun containsAlias(qualifiedTypeAliasName: String): Boolean {
      return _aliases.containsKey(qualifiedTypeAliasName)
   }

   @JvmStatic
   val typeAliases: List<KotlinTypeAlias>
      get() {
         return _aliases.values.toList()
      }
}
