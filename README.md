# Jenkins-Build-Step
A plugin for Jenkins automation server to communicate with IBM UrbanCode Deploy

## Installation
The plugin can be downloaded at the [IBM UrbanCode Plugins website](https://developer.ibm.com/urbancode/plugin/jenkins-build-step/ "Plugin Distributable")

Full instructions can be found in the [Jenkins Build Step plugin documentation](https://developer.ibm.com/urbancode/docs/jenkins-build-step-integration-with-ibm-urbancode-deploy/ "Plugin Documentation")

## Pipeline Examples
### Create Component Version
```groovy
node {
   step([$class: 'UCDeployPublisher',
        siteName: 'local',
        component: [
            $class: 'com.urbancode.jenkins.plugins.ucdeploy.VersionHelper$VersionBlock',
            componentName: 'Jenkins',
            createComponent: [
                $class: 'com.urbancode.jenkins.plugins.ucdeploy.ComponentHelper$CreateComponentBlock',
                componentTemplate: '',
                componentApplication: 'Jenkins'
            ],
            delivery: [
                $class: 'com.urbancode.jenkins.plugins.ucdeploy.DeliveryHelper$Push',
                pushVersion: '${BUILD_NUMBER}',
                baseDir: 'jobs\\test-ucd\\workspace\\build\\distributions',
                fileIncludePatterns: '*.zip',
                fileExcludePatterns: '',
                pushProperties: 'jenkins.server=Local\njenkins.reviewed=false',
                pushDescription: 'Pushed from Jenkins',
                pushIncremental: false
            ]
        ]
    ])
}
```

### Deploy Component
```groovy
node {
   step([$class: 'UCDeployPublisher',
        siteName: 'local',
        deploy: [
            $class: 'com.urbancode.jenkins.plugins.ucdeploy.DeployHelper$DeployBlock',
            deployApp: 'Jenkins',
            deployEnv: 'Test',
            deployProc: 'Deploy Jenkins',
            createProcess: [
                $class: 'com.urbancode.jenkins.plugins.ucdeploy.ProcessHelper$CreateProcessBlock',
                processComponent: 'Deploy'
            ],
            deployVersions: 'Jenkins:${BUILD_NUMBER}',
            deployOnlyChanged: false
        ]
    ])
}
```

### Trigger Version Import
```groovy
node {
   step([$class: 'UCDeployPublisher',
        siteName: 'local',
        component: [
            $class: 'com.urbancode.jenkins.plugins.ucdeploy.VersionHelper$VersionBlock',
            componentName: 'Jenkins',
            createComponent: [
                $class: 'com.urbancode.jenkins.plugins.ucdeploy.ComponentHelper$CreateComponentBlock',
                componentTemplate: '',
                componentApplication: 'Local'
            ],
            delivery: [
                $class: 'com.urbancode.jenkins.plugins.ucdeploy.DeliveryHelper$Pull',
                pullProperties: 'FileSystemImportProperties/name=${BUILD_NUMBER}\nFileSystemImportProperties/description=Pushed from Jenkins',
                pullSourceType: 'File System',
                pullSourceProperties: 'FileSystemComponentProperties/basePath=C:\\Test',
                pullIncremental: false
            ]
        ]
    ])
}
```