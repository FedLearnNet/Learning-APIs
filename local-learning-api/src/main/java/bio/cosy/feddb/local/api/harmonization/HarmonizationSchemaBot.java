package bio.cosy.feddb.local.api.harmonization;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
@ApplicationScoped
public interface HarmonizationSchemaBot {

    @UserMessage("""
            Other files are harmonized to the file with the columns below, which are listed with statistics of their
            values. Describe for every column in one short sentence what it holds, e.g. "Result of the HbA1c blood
            test" or "Number of days in hospital", so columns of other files with other names, codes or languages
            can be matched to it. Also list in sameKind the groups of coded columns whose values are of the same kind,
            e.g. the drugs with the same dosage codes, every column in one group at most; the file holds only a sample,
            so a value one column of a group shows is a value of all. Answer with every column name exactly as given
            and its description, and sameKind, as JSON.
            {#for column in columns}
            - {column}
            {/for}
            """)
    HarmonizationColumnMeaningsDTO describe(List<String> columns);
}
