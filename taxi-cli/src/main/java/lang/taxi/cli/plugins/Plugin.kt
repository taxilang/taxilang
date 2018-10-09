package lang.taxi.cli.plugins

import lang.taxi.plugins.ArtifactId

interface PluginWithConfig<TConfigType> : Plugin {
    fun setConfig(config: TConfigType)
}

interface Plugin {

    val id: ArtifactId
    /**
     * Indicates if this plugin applies to the build plan,
     * given the provided Project and Runtime
     */
//    fun applies(localProject: LocalProject, shipmanContext: ShipmanContext): Boolean

    /**
     * Returns a set of tasks that will be performed.
     *
     *
     * Project and Runtime are provided in case these need
     * to be passed as constructor params.  I think.
     * Not sure if passing these is necessary.
     */
    // TODO : Given this method, applies() seems superfluous -- why not just
    // return an empty set of tasks.  applies() could be internal to the impl.
//    fun getTasks(commandName: CommandName, localProject: LocalProject, shipmanContext: ShipmanContext): List<CommandTask>


}
