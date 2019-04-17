class BuildUtils {
    fun calculateVersion(version: String, branch: String): String {
        return when {
            branch == "develop" -> "$version-SNAPSHOT"
            branch == "master" -> version
            branch.startsWith("release", ignoreCase = true) -> version
            else -> "$version-$branch"
        }
    }

    fun gitBranch(): String {
        return Runtime.getRuntime().exec("git rev-parse --abbrev-ref HEAD")
                .inputStream
                .bufferedReader()
                .readText()
                .trim()
    }
}
