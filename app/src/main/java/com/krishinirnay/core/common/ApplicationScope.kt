package com.krishinirnay.core.common

import javax.inject.Qualifier

/** Application-lifetime [kotlinx.coroutines.CoroutineScope], provided in DataStoreModule. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
