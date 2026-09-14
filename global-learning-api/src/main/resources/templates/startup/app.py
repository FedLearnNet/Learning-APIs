{#if supportsFL}
from pyfedappwrap.learning.base_federated_app import BaseFederatedApp
{#else}
from pyfedappwrap.learning.base_app import BaseApp
{/if}
from pyfedappwrap.learning.run_runfig import AppOutputVisualisations
from config import MyAppConfig, MyAppInputConfig, MyAppOutputConfig


{#if supportsFL}
class {name}(BaseFederatedApp[MyAppConfig, MyAppInputConfig, MyAppOutputConfig]):
{#else}
class {name}(BaseApp[MyAppConfig, MyAppInputConfig, MyAppOutputConfig]):
{/if}

    def __init__(self):
        super().__init__()

    def run_train(self, data: MyAppInputConfig) -> MyAppOutputConfig:


    def run_prediction(self, data: MyAppInputConfig) -> MyAppOutputConfig:


    def _load(self, path: str):
        pass

    def _save(self, path: str):
        pass