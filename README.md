# Overview
This is the [IBM UrbanCode Deploy](https://developer.ibm.com/urbancode/products/urbancode-deploy/) plugin for Jenkins Pipeline (Jenkins 2.0). This plugin is also referred to as the Build Steps plugin since you are able to interact with UrbanCode Deploy via a job step in Jenkins versus a post-processing action. The plugin allows you to upload component versions, create snapshots, and run processes among other things.

More information about this plugin is available [here](https://developer.ibm.com/urbancode/plugin/jenkins-2-0/) and [here](https://developer.ibm.com/urbancode/plugindoc/ibmucd/jenkins-pipeline-formerly-jenkins-2-0/).

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

## Release Notes
### Version 2.3
Fixed APAR PI77548 - Component process properties failing to resolve on deployment.

### Version 2.2
Fixed RFE 98375 - Jenkins Plugin only allows Global credentials instead of job-based credentials.

Fixed PI75045 - UCD server maintenance mode check requires admin privileges.

### Version 2.1
Fixed PI61971 - Connection pool leak in Jenkins ibm-ucdeploy-build-steps.

### Older Versions
Fixed PI32899 - Jenkins plugin fails on slave nodes with an UnserializbleException

Fixed PI36005 - Jenkins plugin 1.2.1 not compatible with builds created with earlier versions of the plugin

Fixed PI37957 - Pulled in a fix for excludes options not being handled by a common library.
