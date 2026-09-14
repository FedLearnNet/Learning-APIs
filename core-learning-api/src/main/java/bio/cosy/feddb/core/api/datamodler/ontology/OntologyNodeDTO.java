package bio.cosy.feddb.core.api.datamodler.ontology;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import bio.cosy.feddb.core.api.datamodler.BaseNodeDTO;
import bio.cosy.feddb.core.api.datamodler.Neo4jNode;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashSet;
import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Data
@Neo4jNode(label = "Ontology")
@JsonIgnoreProperties(ignoreUnknown = true)
public class OntologyNodeDTO extends BaseNodeDTO {

    //Some properties related to https://www.nlm.nih.gov/research/umls/new_users/online_learning/Meta_005.html
    private Set<String> names;
    private String description;
    private Set<String> codes;
    private Set<String> sabs;
    private String cui;
    private Set<String> auis;
    private String lat;


    public void addName(String name) {
        if(this.names == null) {
            this.names = new HashSet<>();
        }
        this.names.add(name);
    }

    public void addCode(String code) {
        if(this.codes == null) {
            this.codes = new HashSet<>();
        }
        this.codes.add(code);
    }

    public void addSab(String sab) {
        if(this.sabs == null) {
            this.sabs = new HashSet<>();
        }
        this.sabs.add(sab);
    }

    public void addAui(String aui) {
        if(this.auis == null) {
            this.auis = new HashSet<>();
        }
        this.auis.add(aui);
    }

    @Override
    public String toString() {
        return "OntologyNodeDTO{" +
                "names='" + names + '\'' +
                ", description='" + description + '\'' +
                ", codes='" + codes + '\'' +
                ", sabs='" + sabs + '\'' +
                '}';
    }

    public static String nodeLabel() {
        return OntologyNodeDTO.label(OntologyNodeDTO.class);
    }
}
