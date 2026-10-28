# Get an Azure Speech key for Vokabellux

You need a **Speech** resource key and its **region**. The app does not use an Azure OpenAI key, and you do not need to create a hosted backend.

1. Open [portal.azure.com](https://portal.azure.com/) and sign in. If you do not have an Azure subscription, first create an account at [azure.microsoft.com/free](https://azure.microsoft.com/free/).
2. Select **Create a resource**, search for **Speech**, and choose Microsoft's Speech service (it may appear under Azure AI services or Foundry Tools).
3. Select your subscription. Create a resource group such as `vokabellux`, choose a unique resource name, and select a region such as **West Europe**. Choose **Free F0** if offered. If you select **Standard S0**, usage is paid: review the pricing shown before creating it.
4. Select **Review + create → Create**, then wait for deployment to finish.
5. Select **Go to resource**. Under **Resource Management**, open **Keys and Endpoint**. Copy **KEY 1** (KEY 2 also works) and note the **Location/Region** identifier. For West Europe, the app expects `westeurope` rather than the friendly name `West Europe`.
6. Open **Vokabellux → Settings**. Enter the key in **Azure Speech key**, enter the exact region identifier, choose a daily request cap, enable processing of submitted recordings, and select **Save settings**. The app connects using key and region; there is no endpoint field to fill in.
7. Start a speaking exercise, allow microphone access, record a short phrase, then tap **Submit for feedback**. Recording and playback alone stay local; Submit sends the recording for assessment.

Enter the key directly into the app. Do not paste it into chat, source files, or Git. The app encrypts the saved key on the device.

If the app reports an authentication problem, check that the key belongs to the same Speech resource as the selected region. If you regenerate the resource key in Azure, replace the old key in the app. A saved key means it has been stored; a successful submitted recording confirms that the subscription works.

The app starts with a cap of 50 speech submission attempts per day. This is a local app limit, not an Azure billing cap. Pronunciation accuracy, fluency, and scripted completeness are baseline assessment features; Microsoft charges baseline assessment at its speech-to-text rate on Standard pricing. Check current limits and costs for your chosen tier rather than assuming that an Azure account or trial makes every resource free.

Official sources checked on 2026-10-28:

- [Microsoft Speech quickstart: create a resource and get its key/region](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/get-started-speech-to-text)
- [Microsoft training: resource fields and Keys and Endpoint](https://microsoftlearning.github.io/AI-102-AIEngineer/Instructions/07-speech.html)
- [Speech pricing, including Free F0 and Standard](https://azure.microsoft.com/en-us/pricing/details/speech/)
- [Pronunciation assessment pricing and included scores](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/pronunciation-assessment-tool)
