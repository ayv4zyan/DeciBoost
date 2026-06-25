package com.deciboost.app.service

class OnboardingIncompleteException :
    IllegalStateException("Onboarding must be completed before starting the boost engine")
