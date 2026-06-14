package observable.net

import net.minecraft.world.level.Level

internal object ClientLevelResolver {
    fun getLevel(): Level? {
        return try {
            val minecraftClass = Class.forName("net.minecraft.client.Minecraft")
            val minecraft = invokeStatic(minecraftClass, "getInstance", "m_91087_") ?: return null
            readField(minecraft, "level", "f_91073_") as? Level
        } catch (_: Throwable) {
            null
        }
    }

    private fun invokeStatic(type: Class<*>, vararg names: String): Any? {
        for (name in names) {
            try {
                val method = type.getDeclaredMethod(name)
                method.isAccessible = true
                return method.invoke(null)
            } catch (_: ReflectiveOperationException) {
            }
        }
        return null
    }

    private fun readField(target: Any, vararg names: String): Any? {
        for (name in names) {
            try {
                val field = target.javaClass.getDeclaredField(name)
                field.isAccessible = true
                return field.get(target)
            } catch (_: ReflectiveOperationException) {
            }
        }
        return null
    }
}
