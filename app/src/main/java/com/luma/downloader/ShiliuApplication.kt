package com.luma.downloader

import android.app.Application

/** Expensive engine initialization happens on demand on an IO dispatcher, never on application startup. */
class ShiliuApplication : Application()
