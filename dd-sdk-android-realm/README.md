# Datadog Integration for Realm

## Getting Started 

To include the Datadog integration for [Realm][1] in your project, simply add the
following to your application's `build.gradle` file.

```
repositories {
    maven { url "https://dl.bintray.com/datadog/datadog-maven" }
}

dependencies {
    implementation "com.datadoghq:dd-sdk-android:<latest-version>"
    implementation "com.datadoghq:dd-sdk-android-rx:<latest-version>"
}
```

### Initial Setup

Before you can use the SDK, you need to setup the library with your application
context, your Client token and your Application ID. 
To generate a Client token and an Application ID please check **UX Monitoring > RUM Applications > New Application**
in the Datadog dashboard.

```kotlin
class SampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val config = DatadogConfig.Builder("<CLIENT_TOKEN>", "<ENVIRONMENT_NAME>", "<APPLICATION_ID>").build()
        Datadog.initialize(this, config)
    }
}
```

If you are using Kotlin we provide a Kotlin extension for the `io.realm.Realm`: `Realm.useWithRum{ }`.
Doing so will automatically intercept any Exception thrown while executing the Realm transaction by creating RUM Error events.

Kotlin: 

```Kotlin
    Realm.getDefaultInstance().useWithRum {
        ...
    }
```

## Contributing

Pull requests are welcome, but please open an issue first to discuss what you
would like to change. For more information, read the 
[Contributing Guide](../CONTRIBUTING.md).

## License

[Apache License, v2.0](../LICENSE)

[1]: https://realm.io/docs/kotlin/latest/
