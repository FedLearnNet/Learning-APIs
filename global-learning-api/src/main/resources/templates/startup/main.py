from pyfedappwrap.engine.config.system_config import system_settings
from pyfedappwrap.engine.runtime import FedDBEngine

{#if supportsFL}
from aggregator import MyAggregator
{/if}
from app import {appName}

print(system_settings)


engine = FedDBEngine()

{#if supportsFL}
engine.register_aggregator(MyAggregator(), "myAgg")
engine.register_federated({appName}())
{#else if type == "DATA_TRANSFORMATION"}
engine.register_transformer({appName}())
{#else}
engine.register({appName}())
{/if}

if __name__ == "__main__":
    engine.start()
    engine.wait_until_stop()
